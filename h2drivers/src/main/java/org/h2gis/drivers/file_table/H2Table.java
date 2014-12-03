/*
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2007-2014 IRSTV (FR CNRS 2488)
 *
 * h2patial is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2spatial is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2spatial. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */

package org.h2gis.drivers.file_table;

import org.h2.api.DatabaseEventListener;
import org.h2.api.ErrorCode;
import org.h2.command.ddl.CreateTableData;
import org.h2.engine.Constants;
import org.h2.engine.Session;
import org.h2.engine.SysProperties;
import org.h2.index.Cursor;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.index.SpatialTreeIndex;
import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.TableBase;
import org.h2.util.MathUtils;
import org.h2.util.New;
import org.h2.value.Value;
import org.h2gis.drivers.FileDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * A table linked with a {@link org.h2gis.drivers.FileDriver}
 * @author Nicolas Fortin
 */
public class H2Table extends TableBase {
    private FileDriver driver;
    private static final Logger LOG = LoggerFactory.getLogger(H2Table.class);
    private final ArrayList<Index> indexes = New.arrayList();
    private Column rowIdColumn;

    public H2Table(FileDriver driver, CreateTableData data) throws IOException {
        super(data);
        indexes.add(new H2TableIndex(driver,this,this.getId(), data.columns.get(0),
                data.schema.getUniqueIndexName(data.session, this,data.tableName + "." +
                        data.columns.get(0).getName() + "_INDEX_")));
        this.driver = driver;
    }
    /**
     * Create row index
     * @param session database session
     */
    public void init(Session session) {
        indexes.add(0, new H2TableIndex(driver,this,this.getId()));
    }

    @Override
    public void lock(Session session, boolean exclusive, boolean force) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void close(Session session) {
        for (Index index : indexes) {
            index.close(session);
        }
        try {
            driver.close();
        } catch (IOException ex) {
            LOG.error("Error while closing the SHP driver", ex);
        }
    }

    @Override
    public void unlock(Session s) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Row getRow(Session session, long key) {
        return indexes.get(0).getRow(session, key);
    }

    @Override
    public Index addIndex(Session session, String indexName, int indexId, IndexColumn[] cols, IndexType indexType, boolean create, String indexComment) {
        boolean isSessionTemporary = isTemporary() && !isGlobalTemporary();
        if (!isSessionTemporary) {
            database.lockMeta(session);
        }
        Index index;
        if (isPersistIndexes() && indexType.isPersistent()) {
            if (indexType.isSpatial()) {
                index = new SpatialTreeIndex(this, indexId, indexName, cols,
                        indexType, true, create, session);
            } else {
                throw DbException.getUnsupportedException("VIEW");
            }
        } else {
            if (indexType.isSpatial()) {
                index = new SpatialTreeIndex(this, indexId, indexName, cols,
                        indexType, false, true, session);
            } else {
                throw DbException.getUnsupportedException("VIEW");
            }
        }
        if (index.needRebuild() && getRowCount(session) > 0) {
            try {
                Index scan = getScanIndex(session);
                long remaining = scan.getRowCount(session);
                long total = remaining;
                Cursor cursor = scan.find(session, null, null);
                long i = 0;
                int bufferSize = (int) Math.min(getRowCount(session), Constants.DEFAULT_MAX_MEMORY_ROWS);
                ArrayList<Row> buffer = New.arrayList(bufferSize);
                String n = getName() + ":" + index.getName();
                int t = MathUtils.convertLongToInt(total);
                while (cursor.next()) {
                    database.setProgress(DatabaseEventListener.STATE_CREATE_INDEX, n,
                            MathUtils.convertLongToInt(i++), t);
                    Row row = cursor.get();
                    buffer.add(row);
                    if (buffer.size() >= bufferSize) {
                        addRowsToIndex(session, buffer, index);
                    }
                    remaining--;
                }
                addRowsToIndex(session, buffer, index);
                if (SysProperties.CHECK && remaining != 0) {
                    throw DbException.throwInternalError("rowcount remaining=" +
                            remaining + " " + getName());
                }
            } catch (DbException e) {
                getSchema().freeUniqueName(indexName);
                try {
                    index.remove(session);
                } catch (DbException e2) {
                    // this could happen, for example on failure in the storage
                    // but if that is not the case it means
                    // there is something wrong with the database
                    trace.error(e2, "could not remove index");
                    throw e2;
                }
                throw e;
            }
        }
        index.setTemporary(isTemporary());
        if (index.getCreateSQL() != null) {
            index.setComment(indexComment);
            if (isSessionTemporary) {
                session.addLocalTempTableIndex(index);
            } else {
                database.addSchemaObject(session, index);
            }
        }
        indexes.add(index);
        setModified();
        return index;
    }

    private static void addRowsToIndex(Session session, ArrayList<Row> list,
                                       Index index) {
        final Index idx = index;
        Collections.sort(list, new Comparator<Row>() {
            @Override
            public int compare(Row r1, Row r2) {
                return idx.compareRows(r1, r2);
            }
        });
        for (Row row : list) {
            index.add(session, row);
        }
        list.clear();
    }

    @Override
    public void removeRow(Session session, Row row) {
        throw DbException.get(ErrorCode.FEATURE_NOT_SUPPORTED_1,"removeRow in Shape files");
    }

    @Override
    public void truncate(Session session) {
        for(Index index : indexes) {
            index.truncate(session);
        }
    }

    @Override
    public void addRow(Session session, Row row) {
        throw DbException.get(ErrorCode.FEATURE_NOT_SUPPORTED_1,"addRow in Shape files");
    }

    @Override
    public void checkSupportAlter() {
        throw DbException.get(ErrorCode.FEATURE_NOT_SUPPORTED_1,"addRow in Shape files");
    }

    @Override
    public String getTableType() {
        return TableBase.EXTERNAL_TABLE_ENGINE;
    }

    @Override
    public Index getScanIndex(Session session) {
        // Look for scan index
        for(Index index : indexes) {
            if(index.getIndexType().isScan()) {
                return index;
            }
        }
        return null;
    }

    @Override
    public Index getUniqueIndex() {
        for (Index idx : indexes) {
            if (idx.getIndexType().isUnique()) {
                return idx;
            }
        }
        return null;
    }

    @Override
    public ArrayList<Index> getIndexes() {
        return indexes;
    }

    @Override
    public boolean isLockedExclusively() {
        return false;
    }

    @Override
    public long getMaxDataModificationId() {
        return 0;
    }

    @Override
    public boolean isDeterministic() {
        return true;
    }

    @Override
    public boolean canGetRowCount() {
        return true;
    }

    @Override
    public boolean canDrop() {
        return true;
    }

    @Override
    public long getRowCount(Session session) {
        return driver.getRowCount();
    }

    @Override
    public long getRowCountApproximation() {
        return driver.getRowCount();
    }

    @Override
    public long getDiskSpaceUsed() {
        return 0;
    }

    @Override
    public void checkRename() {
        //Nothing to check
    }

    @Override
    public Column getRowIdColumn() {
        if (rowIdColumn == null) {
            rowIdColumn = new Column(Column.ROWID, Value.LONG);
            rowIdColumn.setTable(this, -1);
        }
        return rowIdColumn;
    }
}