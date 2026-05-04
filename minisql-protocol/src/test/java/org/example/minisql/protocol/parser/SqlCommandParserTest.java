package org.example.minisql.protocol.parser;

import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.client.ClientOperation;
import org.example.minisql.protocol.command.client.SqlRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlCommandParserTest {
    @Test
    void parsesTableOperations() {
        assertParsed("create table t(id int);;", ClientOperation.CREATE_TABLE, "t", CommandVerb.CREATE);
        assertParsed("select * from t;;", ClientOperation.SELECT, "t", CommandVerb.SELECT);
        assertParsed("insert into t values(1);;", ClientOperation.INSERT, "t", CommandVerb.INSERT);
        assertParsed("delete from t where id = 1;;", ClientOperation.DELETE, "t", CommandVerb.DELETE);
        assertParsed("drop table t;;", ClientOperation.DROP_TABLE, "t", CommandVerb.DROP);
    }

    @Test
    void parsesIndexOperationsWithTableName() {
        assertParsed("create index idx on t(id);;", ClientOperation.CREATE_INDEX, "t", CommandVerb.INSERT);
        assertParsed("drop index idx on t;;", ClientOperation.DROP_INDEX, "t", CommandVerb.DROP);
    }

    @Test
    void rejectsDropIndexWithoutTableName() {
        assertThrows(IllegalArgumentException.class, () -> SqlCommandParser.parse("drop index idx;;"));
    }

    private void assertParsed(String sql, ClientOperation operation, String tableName, CommandVerb verb) {
        SqlRequest request = SqlCommandParser.parse(sql);
        assertEquals(operation, request.operation());
        assertEquals(tableName, request.tableName());
        assertEquals(verb, request.operation().routeVerb());
    }
}
