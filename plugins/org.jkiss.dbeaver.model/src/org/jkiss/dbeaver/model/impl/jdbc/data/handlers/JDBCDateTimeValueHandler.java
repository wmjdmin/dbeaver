/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2016 Serge Rieder (serge@jkiss.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (version 2)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jkiss.dbeaver.model.impl.jdbc.data.handlers;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.data.DBDDataFormatter;
import org.jkiss.dbeaver.model.data.DBDDataFormatterProfile;
import org.jkiss.dbeaver.model.data.DBDDisplayFormat;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.impl.data.DateTimeCustomValueHandler;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

import java.sql.SQLException;
import java.sql.Types;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * JDBC string value handler
 */
public class JDBCDateTimeValueHandler extends DateTimeCustomValueHandler {

    protected static final SimpleDateFormat DEFAULT_DATETIME_FORMAT = new SimpleDateFormat("''" + DBConstants.DEFAULT_TIMESTAMP_FORMAT + "''");
    protected static final SimpleDateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("''" + DBConstants.DEFAULT_DATE_FORMAT + "''");
    protected static final SimpleDateFormat DEFAULT_TIME_FORMAT = new SimpleDateFormat("''" + DBConstants.DEFAULT_TIME_FORMAT + "''");

    public JDBCDateTimeValueHandler(DBDDataFormatterProfile formatterProfile)
    {
        super(formatterProfile);
    }

    @Override
    public Object fetchValueObject(@NotNull DBCSession session, @NotNull DBCResultSet resultSet, @NotNull DBSTypedObject type, int index) throws DBCException {
        try {
            if (resultSet instanceof JDBCResultSet) {
                JDBCResultSet dbResults = (JDBCResultSet) resultSet;
                // It seems that some drivers doesn't support reading date/time values with explicit calendar
                // So let's use simple version
                switch (type.getTypeID()) {
                    case Types.TIME:
                    case Types.TIME_WITH_TIMEZONE:
                        return dbResults.getTime(index + 1);
                    case Types.DATE:
                        return dbResults.getDate(index + 1);
                    default:
                        return dbResults.getTimestamp(index + 1);
                }
            } else {
                return resultSet.getAttributeValue(index);
            }
        }
        catch (SQLException e) {
            if (e.getCause() instanceof ParseException) {
                // [SQLite] workaround.
                try {
                    //return getValueFromObject(session, type, ((JDBCResultSet) resultSet).getObject(index + 1), false);
                    // Do not convert to Date object because table column has STRING type
                    // and it will be converted in string at late binding stage making incorrect string value: Date.toString()
                    return ((JDBCResultSet) resultSet).getObject(index + 1);
                } catch (SQLException e1) {
                    // Ignore
                    log.debug("Can't retrieve datetime object");
                }
            }
            throw new DBCException(e, session.getDataSource());
        }
    }

    @Override
    public void bindValueObject(@NotNull DBCSession session, @NotNull DBCStatement statement, @NotNull DBSTypedObject type, int index, @Nullable Object value) throws DBCException {
        try {
            JDBCPreparedStatement dbStat = (JDBCPreparedStatement)statement;
            // JDBC uses 1-based indexes
            if (value == null) {
                dbStat.setNull(index + 1, type.getTypeID());
            } else {
                switch (type.getTypeID()) {
                    case Types.TIME:
                    case Types.TIME_WITH_TIMEZONE:
                        dbStat.setTime(index + 1, getTimeValue(value));
                        break;
                    case Types.DATE:
                        dbStat.setDate(index + 1, getDateValue(value));
                        break;
                    default:
                        dbStat.setTimestamp(index + 1, getTimestampValue(value));
                        break;
                }
            }
        }
        catch (SQLException e) {
            throw new DBCException(ModelMessages.model_jdbc_exception_could_not_bind_statement_parameter, e);
        }
    }

    @NotNull
    @Override
    public String getValueDisplayString(@NotNull DBSTypedObject column, Object value, @NotNull DBDDisplayFormat format)
    {
        if (format == DBDDisplayFormat.NATIVE) {
            Format nativeFormat = getNativeValueFormat(column);
            if (nativeFormat != null) {
                return nativeFormat.format(value);
            }
        }
        return super.getValueDisplayString(column, value, format);
    }

    @Nullable
    protected Format getNativeValueFormat(DBSTypedObject type) {
        switch (type.getTypeID()) {
            case Types.TIMESTAMP:
                return DEFAULT_DATETIME_FORMAT;
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return DEFAULT_DATETIME_FORMAT;
            case Types.TIME:
                return DEFAULT_TIME_FORMAT;
            case Types.TIME_WITH_TIMEZONE:
                return DEFAULT_TIME_FORMAT;
            case Types.DATE:
                return DEFAULT_DATE_FORMAT;
        }
        return null;
    }

    @NotNull
    protected String getFormatterId(DBSTypedObject column)
    {
        switch (column.getTypeID()) {
            case Types.TIME: return DBDDataFormatter.TYPE_NAME_TIME;
            case Types.DATE: return DBDDataFormatter.TYPE_NAME_DATE;
            default: return DBDDataFormatter.TYPE_NAME_TIMESTAMP;
        }
    }

    @Nullable
    protected static java.sql.Time getTimeValue(Object value)
    {
        if (value instanceof java.sql.Time) {
            return (java.sql.Time) value;
        } else if (value instanceof Date) {
            return new java.sql.Time(((Date) value).getTime());
        } else if (value != null) {
            return java.sql.Time.valueOf(value.toString());
        } else {
            return null;
        }
    }

    @Nullable
    protected static java.sql.Date getDateValue(Object value)
    {
        if (value instanceof java.sql.Date) {
            return (java.sql.Date) value;
        } else if (value instanceof Date) {
            return new java.sql.Date(((Date) value).getTime());
        } else if (value != null) {
            return java.sql.Date.valueOf(value.toString());
        } else {
            return null;
        }
    }

    @Nullable
    protected static java.sql.Timestamp getTimestampValue(Object value)
    {
        if (value instanceof java.sql.Timestamp) {
            return (java.sql.Timestamp) value;
        } else if (value instanceof Date) {
            return new java.sql.Timestamp(((Date) value).getTime());
        } else if (value != null) {
            return java.sql.Timestamp.valueOf(value.toString());
        } else {
            return null;
        }
    }

    protected static String getTwoDigitValue(int value)
    {
        if (value < 10) {
            return "0" + value;
        } else {
            return String.valueOf(value);
        }
    }

}