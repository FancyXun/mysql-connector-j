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

package com.mysql.cj.api.xdevapi;

import java.util.Map;

/**
 * A statement representing a set of row modifications.
 */
public interface UpdateStatement extends Statement<UpdateStatement, Result> {
    /**
     * Add the given set of updates to the statement.
     * 
     * @param fieldsAndValues
     *            table name-value pairs
     * @return {@link UpdateStatement}
     */
    UpdateStatement set(Map<String, Object> fieldsAndValues);

    /**
     * Add the given update to the statement setting field to value for all rows matching the search criteria.
     * 
     * @param field
     *            field name
     * @param value
     *            value to set
     * @return {@link UpdateStatement}
     */
    UpdateStatement set(String field, Object value);

    /**
     * Add/replace the search criteria for this statement.
     * 
     * @param searchCondition
     *            search condition expression
     * @return {@link UpdateStatement}
     */
    UpdateStatement where(String searchCondition);

    /**
     * Add/replace the order specification for this statement.
     * 
     * @param sortFields
     *            sort expression
     * @return {@link UpdateStatement}
     */
    UpdateStatement orderBy(String... sortFields);

    /**
     * Add/replace the row limit for this statement.
     * 
     * @param numberOfRows
     *            limit
     * @return {@link UpdateStatement}
     */
    UpdateStatement limit(long numberOfRows);
}