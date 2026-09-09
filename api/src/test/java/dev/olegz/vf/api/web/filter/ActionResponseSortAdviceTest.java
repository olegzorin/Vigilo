package dev.olegz.vf.api.web.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.api.web.support.ActionResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionResponseSortAdviceTest {
    private static final int SIZE = 110;
    private static final int ROW_COUNT = 100;

    @Test
    void sortResponseBody() {
        ActionResponseSortAdvice sortAdvice = new ActionResponseSortAdvice();
        Map<String, String[]> parameters;
        Response response;

        parameters = sortParams("list", "id", true, 0);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(i, response.list.get(i).id);
        }

        parameters = sortParams("list", "id", true, 4);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(i + 4, response.list.get(i).id);
        }

        parameters = sortParams("list", "time", true, 4);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(i + 4, response.list.get(i).time);
        }

        parameters = sortParams("list", "id", true, -1);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(SIZE - ROW_COUNT + i, response.list.get(i).id);
        }

        parameters = sortParams("list", "id", false, 0);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(SIZE - 1 - i, response.list.get(i).id);
        }

        parameters = sortParams("list", "id", false, 4);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(SIZE - 5 - i, response.list.get(i).id);
        }

        parameters = sortParams("list", "time", false, 4);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(SIZE - 5 - i, response.list.get(i).time);
        }

        parameters = sortParams("list", "id", false, -2);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(ROW_COUNT - i, response.list.get(i).id);
        }

        parameters = sortParams("list", "b", false, -2);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(ROW_COUNT - i, response.list.get(i).b);
        }

        parameters = sortParams("list", "s", false, -2);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(ROW_COUNT - i, response.list.get(i).s);
        }

        parameters = sortParams("list", "integer", false, -2);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(ROW_COUNT - i, response.list.get(i).integer);
        }

        parameters = sortParams("list", "string", false, -2);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.list.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(toString(ROW_COUNT - i), response.list.get(i).string);
        }

        parameters = sortParams("records", "id", true, 0);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.records.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(i, response.records.get(i).id);
        }

        parameters = sortParams("records", "id", false, 0);
        response = newResponse();
        sortAdvice.sortResponseBody(parameters, response);
        assertEquals(ROW_COUNT, response.records.size());
        for (int i = 0; i < ROW_COUNT; i++) {
            assertEquals(SIZE - 1 - i, response.records.get(i).id);
        }
    }

    private Map<String, String[]> sortParams(String collection, String sortBy, boolean asc, int firstRow) {
        return Map.of(
            SortConstants.SORT_COLLECTION, new String[] {collection},
            SortConstants.SORT_BY, new String[] {sortBy},
            SortConstants.SORT_ORDER, new String[] {asc ? "asc" : SortConstants.SORT_ORDER_DESC},
            SortConstants.ROW_COUNT, new String[] {Integer.toString(ROW_COUNT)},
            SortConstants.FIRST_ROW, new String[] {Integer.toString(firstRow)});
    }

    private Response newResponse() {
        Response response = new Response();
        response.list = new ArrayList<>(SIZE);
        response.records = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            Entity e = new Entity();
            e.id = i;
            e.time = i;
            e.b = (byte) i;
            e.s = (short) i;
            e.integer = i;
            e.string = toString(i);
            response.list.add(e);
            response.records.add(new EntityRecord(i));
        }
        return response;
    }

    private String toString(int i) {
        return i < 10 ? "A00" + i : i < 100 ? "a0" + i : "b" + i;
    }

    private static class Response extends ActionResponse {
        public List<Entity> list;
        public List<EntityRecord> records;
    }

    private static class Entity {
        public int id;
        public long time;
        public byte b;
        public short s;
        public Integer integer;
        public String string;
    }

    private record EntityRecord (int id) {}
}
