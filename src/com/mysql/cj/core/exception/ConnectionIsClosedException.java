/*
  Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.

  The MySQL Connector/J is licensed under the terms of the GPLv2
  <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most MySQL Connectors.
  There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
  this software, see the FLOSS License Exception
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

package com.mysql.cj.core.exception;

/**
 * Operation attempted on already closed Connection
 */
public class ConnectionIsClosedException extends CJException {

    private static final long serialVersionUID = -8001652264426656450L;

    public ConnectionIsClosedException() {
        super();
        setSQLState("08003");
    }

    public ConnectionIsClosedException(String message) {
        super(message);
        setSQLState("08003");
    }

    public ConnectionIsClosedException(String message, Throwable cause) {
        super(message, cause);
        setSQLState("08003");
    }

    public ConnectionIsClosedException(Throwable cause) {
        super(cause);
        setSQLState("08003");
    }

    protected ConnectionIsClosedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        setSQLState("08003");
    }

}