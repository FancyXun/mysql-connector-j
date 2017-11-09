/*
  Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FOSS License Exception
  <http://www.mysql.com/about/legal/licensing/foss-exception.html>.

  This program is free software; you can redistribute it and/or modify it under the terms
  of the GNU General Public License as published by the Free Software Foundation; version 2
  of the License.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with this
  program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
  Floor, Boston, MA 02110-1301  USA

 */

package com.mysql.cj.xdevapi;

import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.mysql.cj.api.conf.PropertySet;
import com.mysql.cj.api.conf.ReadableProperty;
import com.mysql.cj.api.result.Row;
import com.mysql.cj.api.xdevapi.Schema;
import com.mysql.cj.api.xdevapi.Session;
import com.mysql.cj.core.conf.PropertyDefinitions;
import com.mysql.cj.core.conf.url.ConnectionUrl;
import com.mysql.cj.core.exceptions.MysqlErrorNumbers;
import com.mysql.cj.core.exceptions.WrongArgumentException;
import com.mysql.cj.core.io.StringValueFactory;
import com.mysql.cj.core.util.StringUtils;
import com.mysql.cj.x.core.MysqlxSession;
import com.mysql.cj.x.core.XDevAPIError;

public class SessionImpl implements Session {

    protected MysqlxSession session;
    protected String defaultSchemaName;

    public SessionImpl(Properties properties) {
        this.session = new MysqlxSession(properties);
        this.session.changeUser(properties.getProperty(PropertyDefinitions.PNAME_user), properties.getProperty(PropertyDefinitions.PNAME_password),
                properties.getProperty(PropertyDefinitions.DBNAME_PROPERTY_KEY));
        this.defaultSchemaName = properties.getProperty(PropertyDefinitions.DBNAME_PROPERTY_KEY);
    }

    protected SessionImpl() {
    }

    public List<Schema> getSchemas() {
        Function<Row, String> rowToName = r -> r.getValue(0, new StringValueFactory());
        Function<Row, Schema> rowToSchema = rowToName.andThen(n -> new SchemaImpl(this.session, this, n));
        return this.session.query("select schema_name from information_schema.schemata", rowToSchema, Collectors.toList());
    }

    public Schema getSchema(String schemaName) {
        return new SchemaImpl(this.session, this, schemaName);
    }

    public String getDefaultSchemaName() {
        return this.defaultSchemaName;
    }

    public Schema getDefaultSchema() {
        if (this.defaultSchemaName == null) {
            throw new WrongArgumentException("Default schema not provided");
        }
        return new SchemaImpl(this.session, this, this.defaultSchemaName);
    }

    public Schema createSchema(String schemaName) {
        StringBuilder stmtString = new StringBuilder("CREATE DATABASE ");
        stmtString.append(StringUtils.quoteIdentifier(schemaName, true));
        this.session.update(stmtString.toString());
        return getSchema(schemaName);
    }

    public Schema createSchema(String schemaName, boolean reuseExistingObject) {
        try {
            return createSchema(schemaName);
        } catch (XDevAPIError ex) {
            if (ex.getErrorCode() == MysqlErrorNumbers.ER_DB_CREATE_EXISTS) {
                return getSchema(schemaName);
            }
            throw ex;
        }
    }

    public void dropSchema(String schemaName) {
        StringBuilder stmtString = new StringBuilder("DROP DATABASE ");
        stmtString.append(StringUtils.quoteIdentifier(schemaName, true));
        this.session.update(stmtString.toString());
    }

    public void startTransaction() {
        this.session.update("START TRANSACTION");
    }

    public void commit() {
        this.session.update("COMMIT");
    }

    public void rollback() {
        this.session.update("ROLLBACK");
    }

    @Override
    public String setSavepoint() {
        return setSavepoint(StringUtils.getUniqueSavepointId());
    }

    @Override
    public String setSavepoint(String name) {
        this.session.update("SAVEPOINT " + StringUtils.quoteIdentifier(name, true));
        return name;
    }

    @Override
    public void rollbackTo(String name) {
        this.session.update("ROLLBACK TO " + StringUtils.quoteIdentifier(name, true));
    }

    @Override
    public void releaseSavepoint(String name) {
        this.session.update("RELEASE SAVEPOINT " + StringUtils.quoteIdentifier(name, true));
    }

    public String getUri() {
        PropertySet pset = this.session.getPropertySet();

        StringBuilder sb = new StringBuilder(ConnectionUrl.Type.XDEVAPI_SESSION.getProtocol());
        sb.append("//").append(this.session.getHost()).append(":").append(this.session.getPort()).append("/").append(this.defaultSchemaName).append("?");

        for (String propName : PropertyDefinitions.PROPERTY_NAME_TO_PROPERTY_DEFINITION.keySet()) {
            ReadableProperty<?> propToGet = pset.getReadableProperty(propName);

            String propValue = propToGet.getStringValue();

            if (propValue != null && !propValue.equals(propToGet.getPropertyDefinition().getDefaultValue().toString())) {
                sb.append(",");
                sb.append(propName);
                sb.append("=");
                sb.append(propValue);
            }
        }

        // TODO modify for multi-host connections

        return sb.toString();

    }

    public boolean isOpen() {
        return this.session.isOpen();
    }

    public void close() {
        this.session.close();
    }

    public SqlStatementImpl sql(String sql) {
        return new SqlStatementImpl(this.session, sql);
    }
}