/*
  Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.

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

package com.mysql.cj.mysqlx.io;

import java.util.ArrayList;
import java.util.function.Function;

import com.google.protobuf.GeneratedMessage;
import com.mysql.cj.core.exceptions.WrongArgumentException;
import com.mysql.cj.core.io.StatementExecuteOk;
import com.mysql.cj.jdbc.Field;
import com.mysql.cj.mysqlx.io.AsyncMessageReader.MessageListener;
import com.mysql.cj.mysqlx.protobuf.MysqlxResultset.ColumnMetaData;
import com.mysql.cj.mysqlx.protobuf.MysqlxResultset.FetchDone;
import com.mysql.cj.mysqlx.protobuf.MysqlxResultset.Row;
import com.mysql.cj.mysqlx.protobuf.MysqlxSql.StmtExecuteOk;
import com.mysql.cj.mysqlx.result.MysqlxRow;

/**
 * A {@link MessageListener} to handle result data and propagate it to a {@link ResultListener}.
 */
public class ResultMessageListener implements MessageListener {
    private ResultListener callbacks;
    private ColToFieldTransformer colToField;

    /**
     * Accumulate metadata before delivering to client.
     */
    private ArrayList<Field> metadata = new ArrayList<>();

    /**
     * Have we finished reading metadata and send it to client yet?
     */
    private boolean metadataSent = false;

    /**
     * The type for the function to transform the {@link ColumnMetaData} to a {@link Field}.
     */
    public static interface ColToFieldTransformer extends Function<ColumnMetaData, Field> {}

    public ResultMessageListener(ColToFieldTransformer colToField, ResultListener callbacks) {
        this.callbacks = callbacks;
        this.colToField = colToField;
    }

    private boolean handleColumn(ColumnMetaData col) {
        Field f = this.colToField.apply(col);
        this.metadata.add(f);
        return false; /* done reading? */
    }

    private boolean handleFetchDone() {
        if (!this.metadataSent) {
            callbacks.onMetadata(this.metadata);
            this.metadataSent = true;
        }
        return false; /* done reading? */
    }

    private boolean handleRow(Row r) {
        MysqlxRow row = new MysqlxRow(this.metadata, r);
        callbacks.onRow(row);
        return false; /* done reading? */
    }

    private boolean handleStmtExecuteOk() {
        callbacks.onComplete(new StatementExecuteOk(0, null, new ArrayList<>()));
        return true; /* done reading? */
    }

    private boolean handleError(Error error) {
        MysqlxError e = new MysqlxError(error);
        callbacks.onError(e);
        return true; /* done reading? */
    }

    private void handleException(Throwable ex) {
        callbacks.onException(ex);
        return true; /* done reading? */
    }

    public Boolean apply(Class<? extends GeneratedMessage> msgClass, GeneratedMessage msg) {
        if (StmtExecuteOk.class.equals(msgClass)) {
            return handleStmtExecuteOk();
        } else if (ColumnMetaData.class.equals(msgClass)) {
            return handleColumn(ColumnMetaData.class.cast(msg));
        } else if (FetchDone.class.equals(msgClass)) {
            return handleFetchDone();
        } else if (Row.class.equals(msgClass)) {
            return handleRow(Row.class.cast(msg));
        } else if (Error.class.equals(msgClass)) {
            return handleError(Error.class.cast(msg));
        } else if (Frame.class.equals(msgClass)) {
            // TODO warnings, update counts, etc
        } else {
            return handleException(new WrongArgumentException("Unhandled msg class (" + msgClass + ") + msg=" + msg));
        }
    }

    public void closed() {
    }

    public void error(Throwable ex) {
        handleException(ex);
    }
}
