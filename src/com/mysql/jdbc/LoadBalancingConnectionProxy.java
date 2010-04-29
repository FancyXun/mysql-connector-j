/*
  Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPL,
  like most MySQL Connectors. There are special exceptions to the
  terms and conditions of the GPL as it is applied to this software,
  see the FLOSS License Exception available on mysql.com.

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License as
  published by the Free Software Foundation; version 2 of the
  License.

  This program is distributed in the hope that it will be useful,  
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  02110-1301 USA
 
 */
package com.mysql.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * An implementation of java.sql.Connection that load balances requests across a
 * series of MySQL JDBC connections, where the balancing takes place at
 * transaction commit.
 * 
 * Therefore, for this to work (at all), you must use transactions, even if only
 * reading data.
 * 
 * This implementation will invalidate connections that it detects have had
 * communication errors when processing a request. Problematic hosts will be
 * added to a global blacklist for loadBalanceBlacklistTimeout ms, after which
 * they will be removed from the blacklist and made eligible once again to be
 * selected for new connections.
 * 
 * This implementation is thread-safe, but it's questionable whether sharing a
 * connection instance amongst threads is a good idea, given that transactions
 * are scoped to connections in JDBC.
 * 
 * @version $Id: $
 * 
 */
public class LoadBalancingConnectionProxy implements InvocationHandler,
		PingTarget {

	private static Method getLocalTimeMethod;
	
	private long totalPhysicalConnections = 0;
	private long activePhysicalConnections = 0;
	private String hostToRemove = null;
	private long lastUsed = 0;
	private long transactionCount = 0;
	private ConnectionGroup connectionGroup = null;

	public static final String BLACKLIST_TIMEOUT_PROPERTY_KEY = "loadBalanceBlacklistTimeout";

	static {
		try {
			getLocalTimeMethod = System.class.getMethod("nanoTime",
					new Class[0]);
		} catch (SecurityException e) {
			// ignore
		} catch (NoSuchMethodException e) {
			// ignore
		}
	}

	// Lifted from C/J 5.1's JDBC-2.0 connection pool classes, let's merge this
	// if/when this gets into 5.1
	protected class ConnectionErrorFiringInvocationHandler implements
			InvocationHandler {
		Object invokeOn = null;

		public ConnectionErrorFiringInvocationHandler(Object toInvokeOn) {
			invokeOn = toInvokeOn;
		}

		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			Object result = null;

			try {
				result = method.invoke(invokeOn, args);

				if (result != null) {
					result = proxyIfInterfaceIsJdbc(result, result.getClass());
				}
			} catch (InvocationTargetException e) {
				dealWithInvocationException(e);
			}

			return result;
		}
	}

	protected MySQLConnection currentConn;

	protected List hostList;

	protected Map liveConnections;

	private Map connectionsToHostsMap;

	private long[] responseTimes;

	private Map hostsToListIndexMap;

	private boolean inTransaction = false;

	private long transactionStartTime = 0;

	private Properties localProps;

	private boolean isClosed = false;

	private BalanceStrategy balancer;

	private int retriesAllDown;

	private static Map globalBlacklist = new HashMap();

	private int globalBlacklistTimeout = 0;
	
	private long connectionGroupProxyID = 0;

	private LoadBalanceExceptionChecker exceptionChecker;

	private Map jdbcInterfacesForProxyCache = new HashMap();
	
	private MySQLConnection thisAsConnection = null;
	
	




	/**
	 * Creates a proxy for java.sql.Connection that routes requests between the
	 * given list of host:port and uses the given properties when creating
	 * connections.
	 * 
	 * @param hosts
	 * @param props
	 * @throws SQLException
	 */
	LoadBalancingConnectionProxy(List hosts, Properties props)
			throws SQLException {
		String group = props.getProperty("loadBalanceConnectionGroup",
				null);
		boolean enableJMX = false;
		String enableJMXAsString = props.getProperty("loadBalanceEnableJMX",
				"false");
		try{
			enableJMX = Boolean.parseBoolean(enableJMXAsString);
		} catch (Exception e){
			throw SQLError.createSQLException(Messages.getString(
					"LoadBalancingConnectionProxy.badValueForLoadBalanceEnableJMX",
					new Object[] { enableJMXAsString }),
					SQLError.SQL_STATE_ILLEGAL_ARGUMENT, null);			
		}
		
		if(group != null){
			this.connectionGroup = ConnectionGroupManager.getConnectionGroupInstance(group);
			if(enableJMX){
				ConnectionGroupManager.registerJmx();
			}
			this.connectionGroupProxyID = this.connectionGroup.registerConnectionProxy(this, hosts);
			hosts = new ArrayList(this.connectionGroup.getInitialHosts());
		}
		
		this.hostList = hosts;

		int numHosts = this.hostList.size();

		this.liveConnections = new HashMap(numHosts);
		this.connectionsToHostsMap = new HashMap(numHosts);
		this.responseTimes = new long[numHosts];
		this.hostsToListIndexMap = new HashMap(numHosts);

		this.localProps = (Properties) props.clone();
		this.localProps.remove(NonRegisteringDriver.HOST_PROPERTY_KEY);
		this.localProps.remove(NonRegisteringDriver.PORT_PROPERTY_KEY);

		for (int i = 0; i < numHosts; i++) {
			this.hostsToListIndexMap.put(this.hostList.get(i), new Integer(i));
			this.localProps.remove(NonRegisteringDriver.HOST_PROPERTY_KEY + "."
					+ (i + 1));
			this.localProps.remove(NonRegisteringDriver.PORT_PROPERTY_KEY + "."
					+ (i + 1));
		}

		this.localProps.remove(NonRegisteringDriver.NUM_HOSTS_PROPERTY_KEY);
		this.localProps.setProperty("useLocalSessionState", "true");

		String strategy = this.localProps.getProperty("loadBalanceStrategy",
				"random");
		
		String lbExceptionChecker = this.localProps.getProperty("loadBalanceExceptionChecker",
		"com.mysql.jdbc.StandardLoadBalanceExceptionChecker");


		String retriesAllDownAsString = this.localProps.getProperty(
				"retriesAllDown", "120");

		try {
			this.retriesAllDown = Integer.parseInt(retriesAllDownAsString);
		} catch (NumberFormatException nfe) {
			throw SQLError.createSQLException(Messages.getString(
					"LoadBalancingConnectionProxy.badValueForRetriesAllDown",
					new Object[] { retriesAllDownAsString }),
					SQLError.SQL_STATE_ILLEGAL_ARGUMENT, null);
		}
		String blacklistTimeoutAsString = this.localProps.getProperty(
				BLACKLIST_TIMEOUT_PROPERTY_KEY, "0");

		try {
			this.globalBlacklistTimeout = Integer
					.parseInt(blacklistTimeoutAsString);
		} catch (NumberFormatException nfe) {
			throw SQLError
					.createSQLException(
							Messages
									.getString(
											"LoadBalancingConnectionProxy.badValueForLoadBalanceBlacklistTimeout",
											new Object[] { retriesAllDownAsString }),
							SQLError.SQL_STATE_ILLEGAL_ARGUMENT, null);
		}

		if ("random".equals(strategy)) {
			this.balancer = (BalanceStrategy) Util.loadExtensions(null, props,
					"com.mysql.jdbc.RandomBalanceStrategy",
					"InvalidLoadBalanceStrategy", null).get(0);
		} else if ("bestResponseTime".equals(strategy)) {
			this.balancer = (BalanceStrategy) Util.loadExtensions(null, props,
					"com.mysql.jdbc.BestResponseTimeBalanceStrategy",
					"InvalidLoadBalanceStrategy", null).get(0);
		} else {
			this.balancer = (BalanceStrategy) Util.loadExtensions(null, props,
					strategy, "InvalidLoadBalanceStrategy", null).get(0);
		}

		this.balancer.init(null, props);
		

		this.exceptionChecker = (LoadBalanceExceptionChecker) Util.loadExtensions(null, props,
				lbExceptionChecker, "InvalidLoadBalanceExceptionChecker", null).get(0);
		this.exceptionChecker.init(null, props);

		thisAsConnection = new LoadBalancedMySQLConnection(this);

		
		pickNewConnection();
		
		
		
	}

	/**
	 * Creates a new physical connection for the given host:port and updates
	 * required internal mappings and statistics for that connection.
	 * 
	 * @param hostPortSpec
	 * @return
	 * @throws SQLException
	 */
	public synchronized ConnectionImpl createConnectionForHost(
			String hostPortSpec) throws SQLException {
		Properties connProps = (Properties) this.localProps.clone();

		String[] hostPortPair = NonRegisteringDriver
				.parseHostPortPair(hostPortSpec);
		String hostName = hostPortPair[NonRegisteringDriver.HOST_NAME_INDEX];
		String portNumber = hostPortPair[NonRegisteringDriver.PORT_NUMBER_INDEX];
		String dbName = connProps
				.getProperty(NonRegisteringDriver.DBNAME_PROPERTY_KEY);

		if (hostName == null) {
			throw new SQLException(
					"Could not find a hostname to start a connection to");
		}
		if (portNumber == null) {
			portNumber = "3306";// use default
		}

		connProps.setProperty(NonRegisteringDriver.HOST_PROPERTY_KEY, hostName);
		connProps.setProperty(NonRegisteringDriver.PORT_PROPERTY_KEY,
				portNumber);
		connProps.setProperty(NonRegisteringDriver.HOST_PROPERTY_KEY + ".1",
				hostName);
		connProps.setProperty(NonRegisteringDriver.PORT_PROPERTY_KEY + ".1",
				portNumber);
		connProps.setProperty(NonRegisteringDriver.NUM_HOSTS_PROPERTY_KEY, "1");
		connProps.setProperty("roundRobinLoadBalance", "false"); // make sure we
																	// don't
																	// pickup
																	// the
																	// default
																	// value

		ConnectionImpl conn = (ConnectionImpl) ConnectionImpl.getInstance(
				hostName, Integer.parseInt(portNumber), connProps, dbName,
				"jdbc:mysql://" + hostName + ":" + portNumber + "/");
		
		

		this.liveConnections.put(hostPortSpec, conn);
		this.connectionsToHostsMap.put(conn, hostPortSpec);
		

		this.activePhysicalConnections++;
		this.totalPhysicalConnections++;

		conn.setProxy(this.thisAsConnection);

		return conn;
	}

	/**
	 * @param e
	 * @throws SQLException
	 * @throws Throwable
	 * @throws InvocationTargetException
	 */
	void dealWithInvocationException(InvocationTargetException e)
			throws SQLException, Throwable, InvocationTargetException {
		Throwable t = e.getTargetException();

		if (t != null) {
			if (t instanceof SQLException && shouldExceptionTriggerFailover((SQLException) t )) {
				invalidateCurrentConnection();
				pickNewConnection();
			}

			throw t;
		}

		throw e;
	}

	/**
	 * Closes current connection and removes it from required mappings.
	 * 
	 * @throws SQLException
	 */
	synchronized void invalidateCurrentConnection() throws SQLException {
		try {
			if (!this.currentConn.isClosed()) {
				this.currentConn.close();
			}

		} finally {
			// add host to the global blacklist, if enabled
			if (this.isGlobalBlacklistEnabled()) {
				this.addToGlobalBlacklist((String) this.connectionsToHostsMap
						.get(this.currentConn));

			}
			// remove from liveConnections
			this.liveConnections.remove(this.connectionsToHostsMap
					.get(this.currentConn));
			Object mappedHost = this.connectionsToHostsMap
					.remove(this.currentConn);
			if (mappedHost != null
					&& this.hostsToListIndexMap.containsKey(mappedHost)) {
				int hostIndex = ((Integer) this.hostsToListIndexMap
						.get(mappedHost)).intValue();
				// reset the statistics for the host
				synchronized (this.responseTimes) {
					this.responseTimes[hostIndex] = 0;
				}
			}
		}
	}

	private void closeAllConnections() {
		synchronized (this) {
			// close all underlying connections
			Iterator allConnections = this.liveConnections.values().iterator();

			while (allConnections.hasNext()) {
				try {
					this.activePhysicalConnections--;
					((Connection) allConnections.next()).close();
				} catch (SQLException e) {
				}
			}

			if (!this.isClosed) {
				this.balancer.destroy();
				if(this.connectionGroup != null){
					this.connectionGroup.closeConnectionProxy(this);
				}
			}

			this.liveConnections.clear();
			this.connectionsToHostsMap.clear();
		}

	}

	/**
	 * Proxies method invocation on the java.sql.Connection interface, trapping
	 * "close", "isClosed" and "commit/rollback" (to switch connections for load
	 * balancing).
	 */
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {

		String methodName = method.getName();
		
		if("getLoadBalanceSafeProxy".equals(methodName)){
			return this.currentConn;
		}

		if ("equals".equals(methodName) && args.length == 1) {
			if (args[0] instanceof Proxy) {
				return Boolean.valueOf((((Proxy) args[0]).equals(this)));
			}
			return Boolean.valueOf(this.equals(args[0]));
		}

		if ("hashCode".equals(methodName)) {
			return new Integer(this.hashCode());
		}

		if ("close".equals(methodName)) {
			closeAllConnections();
			this.isClosed = true;
			return null;
		}

		if ("isClosed".equals(methodName)) {
			return Boolean.valueOf(this.isClosed);
		}

		if (this.isClosed) {
			throw SQLError.createSQLException(
					"No operations allowed after connection closed.",
					SQLError.SQL_STATE_CONNECTION_NOT_OPEN, null /*
																 * no access to
																 * a interceptor
																 * here...
																 */);
		}

		if (!inTransaction) {
			this.inTransaction = true;
			this.transactionStartTime = getLocalTimeBestResolution();
			this.transactionCount++;
		}

		Object result = null;

		try {
			this.lastUsed = System.currentTimeMillis();
			result = method.invoke(thisAsConnection, args);

			if (result != null) {
				if (result instanceof com.mysql.jdbc.Statement) {
					((com.mysql.jdbc.Statement) result).setPingTarget(this);
				}

				result = proxyIfInterfaceIsJdbc(result, result.getClass());
			}
		} catch (InvocationTargetException e) {
			dealWithInvocationException(e);
		} finally {
			if ("commit".equals(methodName) || "rollback".equals(methodName)) {
				this.inTransaction = false;

				// Update stats
				String host = (String) this.connectionsToHostsMap
						.get(this.currentConn);
				// avoid NPE if the connection has already been removed from
				// connectionsToHostsMap
				// in invalidateCurrenctConnection()
				if (host != null) {
					synchronized (this.responseTimes) {
						int hostIndex = ((Integer) this.hostsToListIndexMap
								.get(host)).intValue();

						if(hostIndex < this.responseTimes.length){
							this.responseTimes[hostIndex] = getLocalTimeBestResolution()
									- this.transactionStartTime;
						}
					}
				}
				pickNewConnection();
			}
		}

		return result;
	}

	/**
	 * Picks the "best" connection to use for the next transaction based on the
	 * BalanceStrategy in use.
	 * 
	 * @throws SQLException
	 */
	protected synchronized void pickNewConnection() throws SQLException {
		if (this.currentConn == null) { // startup
			this.currentConn = this.balancer.pickConnection(this, Collections
					.unmodifiableList(this.hostList), Collections
					.unmodifiableMap(this.liveConnections),
					(long[]) this.responseTimes.clone(), this.retriesAllDown);
			return;
		}
		
		if(this.currentConn.isClosed()){
			invalidateCurrentConnection();
		}

		boolean currentAutoCommit = this.currentConn.getAutoCommit();
		int currentTransIsolation = this.currentConn.getTransactionIsolation();
		int pingTimeout = this.currentConn.getLoadBalancePingTimeout();
		boolean pingBeforeReturn = this.currentConn.getLoadBalanceValidateConnectionOnSwapServer();
		for(int hostsTried = 0, hostsToTry = this.hostList.size(); hostsTried <= hostsToTry; hostsTried++){
			try{
				ConnectionImpl newConn = (ConnectionImpl) this.balancer.pickConnection(

					this, Collections.unmodifiableList(this.hostList), Collections
							.unmodifiableMap(this.liveConnections),
					(long[]) this.responseTimes.clone(), this.retriesAllDown);
		
				if (this.currentConn != null) {
					if(pingBeforeReturn){
						if(pingTimeout == 0){
							newConn.ping();
						} else {
							newConn.pingInternal(true, pingTimeout);
						}
					}
					newConn.setTransactionIsolation(currentTransIsolation);
					newConn.setAutoCommit(currentAutoCommit);
				}
				
				this.currentConn = newConn;
				return;
			} catch (SQLException e){
				
				if (shouldExceptionTriggerFailover(e)) {
					// connection error, close up shop on current
					// connection
					invalidateCurrentConnection();
				}
			}

		}
		// no hosts available to swap connection to, close up.
		this.isClosed = true;
	}

	/**
	 * Recursively checks for interfaces on the given object to determine if it
	 * implements a java.sql interface, and if so, proxies the instance so that
	 * we can catch and fire SQL errors.
	 * 
	 * @param toProxy
	 * @param clazz
	 * @return
	 */
	Object proxyIfInterfaceIsJdbc(Object toProxy, Class clazz) {
		
		if(isInterfaceJdbc(clazz)){
				return Proxy.newProxyInstance(toProxy.getClass()
						.getClassLoader(), clazz.getInterfaces(),
						createConnectionProxy(toProxy));
			}

		return toProxy;
	}
	
	
	private boolean isInterfaceJdbc(Class clazz){
		if(this.jdbcInterfacesForProxyCache.containsKey(clazz)){
			return ((Boolean) this.jdbcInterfacesForProxyCache.get(clazz)).booleanValue();
		}
		Class[] interfaces = clazz.getInterfaces();

		for (int i = 0; i < interfaces.length; i++) {
			String packageName = interfaces[i].getPackage().getName();

			if ("java.sql".equals(packageName)
					|| "javax.sql".equals(packageName)
					|| "com.mysql.jdbc".equals(packageName)) {
				this.jdbcInterfacesForProxyCache.put(clazz, new Boolean(true));
				return true;
			}

			if(isInterfaceJdbc(interfaces[i])){
				this.jdbcInterfacesForProxyCache.put(clazz, new Boolean(true));
				return true;
			}
		}

		this.jdbcInterfacesForProxyCache.put(clazz, new Boolean(false));
		return false;
		
	}

	protected ConnectionErrorFiringInvocationHandler createConnectionProxy(
			Object toProxy) {
		return new ConnectionErrorFiringInvocationHandler(toProxy);
	}

	/**
	 * Returns best-resolution representation of local time, using nanoTime() if
	 * availble, otherwise defaulting to currentTimeMillis().
	 */
	private static long getLocalTimeBestResolution() {
		if (getLocalTimeMethod != null) {
			try {
				return ((Long) getLocalTimeMethod.invoke(null, null))
						.longValue();
			} catch (IllegalArgumentException e) {
				// ignore - we fall through to currentTimeMillis()
			} catch (IllegalAccessException e) {
				// ignore - we fall through to currentTimeMillis()
			} catch (InvocationTargetException e) {
				// ignore - we fall through to currentTimeMillis()
			}
		}

		return System.currentTimeMillis();
	}

	public synchronized void doPing() throws SQLException {
		SQLException se = null;
		boolean foundHost = false;
		int pingTimeout = this.currentConn.getLoadBalancePingTimeout();
		synchronized (this) {
			for (Iterator i = this.hostList.iterator(); i.hasNext();) {
				String host = (String) i.next();
				Connection conn = (Connection) this.liveConnections.get(host);
				if (conn == null) {
					continue;
				}
				try {
					if(pingTimeout == 0){
						conn.ping();
					} else {
						((ConnectionImpl) conn).pingInternal(true, pingTimeout);
					}
					foundHost = true;
				} catch (SQLException e) {
					this.activePhysicalConnections--;
					// give up if it is the current connection, otherwise NPE
					// faking resultset later.
					if (host.equals(this.connectionsToHostsMap
							.get(this.currentConn))) {
						// clean up underlying connections, since connection
						// pool won't do it
						closeAllConnections();
						this.isClosed = true;
						throw e;
					}

					// if the Exception is caused by ping connection lifetime
					// checks, don't add to blacklist
					if (e
							.getMessage()
							.equals(
									Messages
											.getString("Connection.exceededConnectionLifetime"))) {
						// only set the return Exception if it's null
						if (se == null) {
							se = e;
						}
					} else {
						// overwrite the return Exception no matter what
						se = e;
						if (this.isGlobalBlacklistEnabled()) {
							this.addToGlobalBlacklist(host);
						}
					}
					// take the connection out of the liveConnections Map
					this.liveConnections.remove(this.connectionsToHostsMap
							.get(conn));
				}
			}
		}
		// if there were no successful pings
		if (!foundHost) {
			closeAllConnections();
			this.isClosed = true;
			// throw the stored Exception, if exists
			if (se != null) {
				throw se;
			}
			// or create a new SQLException and throw it, must be no
			// liveConnections
			((ConnectionImpl) this.currentConn)
					.throwConnectionClosedException();
		}
	}

	public void addToGlobalBlacklist(String host, long timeout) {
		if (this.isGlobalBlacklistEnabled()) {
			synchronized (globalBlacklist) {
				globalBlacklist.put(host, new Long(timeout));
			}
		}
	}
	
	public void addToGlobalBlacklist(String host){
		addToGlobalBlacklist(host, System.currentTimeMillis()
						+ this.globalBlacklistTimeout);
		
	}

	public boolean isGlobalBlacklistEnabled() {
		return (this.globalBlacklistTimeout > 0);
	}

	public Map getGlobalBlacklist() {
		if (!this.isGlobalBlacklistEnabled()) {
			String localHostToRemove = this.hostToRemove;
			if(hostToRemove != null){
				HashMap fakedBlacklist = new HashMap();
				fakedBlacklist.put(localHostToRemove, new Long(System.currentTimeMillis() + 5000));
				return fakedBlacklist;
			}
			return new HashMap(1);
		}

		// Make a local copy of the blacklist
		Map blacklistClone = new HashMap(globalBlacklist.size());
		// Copy everything from synchronized global blacklist to local copy for
		// manipulation
		synchronized (globalBlacklist) {
			blacklistClone.putAll(globalBlacklist);
		}
		Set keys = blacklistClone.keySet();

		// we're only interested in blacklisted hosts that are in the hostList
		keys.retainAll(this.hostList);
		if (keys.size() == this.hostList.size()) {
			// return an empty blacklist, let the BalanceStrategy
			// implementations try to connect to everything
			// since it appears that all hosts are unavailable - we don't want
			// to wait for
			// loadBalanceBlacklistTimeout to expire.
			return new HashMap(1);
		}

		// Don't need to synchronize here as we using a local copy
		for (Iterator i = keys.iterator(); i.hasNext();) {
			String host = (String) i.next();
			// OK if null is returned because another thread already purged Map
			// entry.
			Long timeout = (Long) globalBlacklist.get(host);
			if (timeout != null
					&& timeout.longValue() < System.currentTimeMillis()) {
				// Timeout has expired, remove from blacklist
				synchronized (globalBlacklist) {
					globalBlacklist.remove(host);
				}
				i.remove();
			}

		}
		
		return blacklistClone;
	}

	public boolean shouldExceptionTriggerFailover(SQLException ex){
		return this.exceptionChecker.shouldExceptionTriggerFailover(ex);
		
	}
	
	public void removeHostWhenNotInUse(String host) throws SQLException {
		int timeBetweenChecks = 1000;
		long timeBeforeHardFail = 15000;
		addToGlobalBlacklist(host, timeBeforeHardFail + 1000);
		long cur = System.currentTimeMillis();
		while(System.currentTimeMillis() - timeBeforeHardFail < cur){
			synchronized (this){
				this.hostToRemove = host;
				if(!host.equals(this.currentConn.getHost())){
					removeHost(host);
					return;
				}
			}
			try {
				Thread.currentThread().sleep(timeBetweenChecks);
			} catch (InterruptedException e) {
				// better to swallow this and retry.
			}
			
		}
		removeHost(host);
	}
	
	public void removeHost(String host) throws SQLException {
		synchronized(this){
			if(this.connectionGroup != null){
				if(this.connectionGroup.getInitialHosts().size() == 1 && this.connectionGroup.getInitialHosts().contains(host)){
					throw SQLError.createSQLException("Cannot remove only configured host.", null);
				}
			}
			this.hostToRemove = host;
			if(host.equals(this.currentConn.getHost())){
				closeAllConnections();
			} else {
				this.connectionsToHostsMap.remove(this.liveConnections.remove(host));
				Integer idx = (Integer) this.hostsToListIndexMap.remove(host);
				long[] newResponseTimes = new long[this.responseTimes.length - 1];
				int newIdx = 0;
				for(Iterator i = this.hostList.iterator(); i.hasNext(); newIdx++){
					String copyHost = i.next().toString();
					if(idx != null && idx.intValue() < this.responseTimes.length){
						newResponseTimes[newIdx] = this.responseTimes[idx.intValue()];
						this.hostsToListIndexMap.put(copyHost, new Integer(newIdx));
					}
				}
				this.responseTimes = newResponseTimes;
			}
		}
		
	}
	
	public synchronized boolean addHost(String host){
		synchronized(this){
			if(this.hostsToListIndexMap.containsKey(host)){
				return false;
			}
			long[] newResponseTimes = new long[this.responseTimes.length + 1];
			for(int i = 0; i < this.responseTimes.length; i++){
				newResponseTimes[i] = this.responseTimes[i];
			}
			this.responseTimes = newResponseTimes;
			this.hostList.add(host);
			this.hostsToListIndexMap.put(host, new Integer(this.responseTimes.length - 1));
		}
		return true;
	}
	

	
	public long getLastUsed(){
		return this.lastUsed;
	}
	
	public boolean inTransaction(){
		return this.inTransaction;
	}
	
	public long getTransactionCount(){
		return this.transactionCount;
	}
	
	public long getActivePhysicalConnectionCount(){
		return this.activePhysicalConnections;
	}
	public long getTotalPhysicalConnectionCount(){
		return this.totalPhysicalConnections;
	}
	
	public long getConnectionGroupProxyID(){
		return this.connectionGroupProxyID;
	}
		
	
}