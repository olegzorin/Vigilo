package dev.olegz.vf.report.domain;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

public class ReportData {
    private static final HashMap<String, Field> FIELDS_MAP;

    static {
        Field[] fields = ReportData.class.getDeclaredFields();
        FIELDS_MAP = new HashMap<>(fields.length);
        for (Field field : fields) {
            FIELDS_MAP.put(field.getName().toUpperCase(), field);
        }
    }

    public int id;

    public String name, description,
        s1, s2, s3, s4, s5, s6, s7, s8, s9, s10,
        s11, s12, s13, s14, s15, s16, s17, s18, s19, s20;

    public Double n1, n2, n3, n4, n5, n6, n7, n8, n9, n10,
        n11, n12, n13, n14, n15, n16, n17, n18, n19, n20,
        n21, n22, n23, n24, n25, n26, n27, n28, n29, n30,
        n31, n32, n33, n34, n35, n36, n37, n38, n39, n40;

    public Timestamp t1, t2, t3, t4, t5, t6, t7, t8, t9, t10,
        t11, t12, t13, t14, t15, t16, t17, t18, t19, t20;

    public Boolean b1, b2, b3, b4, b5, b6, b7, b8, b9, b10;

    public Object getValue(String column) {
        Field field = FIELDS_MAP.get(column.toUpperCase());
        if (field != null) {
            try {
                return field.get(this);
            } catch (IllegalAccessException ignore) {
            }
        }
        return null;
    }

    @Override
    public String toString() {
        ArrayList<String> items = new ArrayList<>(80);
        FIELDS_MAP.forEach((fieldName, field) -> {
            try {
                Object object = FIELDS_MAP.get(fieldName).get(this);
                if (object != null) items.add(fieldName + "=" + object);
            } catch (IllegalAccessException e) {
                items.add(fieldName + "=" + e);
            }
        });
        return "ReportData{" + String.join(", ", items) + '}';

    }
}
