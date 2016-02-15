package de.deverado.framework.guice;

import static org.junit.Assert.assertEquals;

import de.deverado.framework.core.problemreporting.Problem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
public class GuiceUtilTest {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testBindConfig() throws Exception {
        Properties configProps = new Properties();
        HashMap<Object, Object> configMap = new HashMap<>();
        configMap.put("some.val", "val");
        configMap.put("some.int", "12");
        configMap.put("some.long", "" + (Integer.MAX_VALUE + 1l));
        configMap.put("some.bool", "true");
        configMap.put("optional.string.google", "optval");
        configProps.putAll(configMap);
        TestBindConfigClass target = new TestBindConfigClass();
        List<Problem> problems = GuiceUtil.bindConfig(configProps, target, true, false);
        assertEquals("" + problems, 0, problems.size());

        assertEquals("val", target.getSomeValJavax());
        assertEquals("val", target.someValGoogle);
        assertEquals(Integer.valueOf(12), target.someInt);
        assertEquals(12, target.someIntPrimitive);
        assertEquals(Integer.MAX_VALUE + 1l, target.someLong.longValue());
        assertEquals(Boolean.TRUE, target.someBool);
        assertEquals("optval", target.optionalStringGoogle);
    }

    @Test
    public void testBindConfigOptional() throws Exception {
        Properties configProps = new Properties();
        TestBindConfigOptionalClass target = new TestBindConfigOptionalClass();
        List<Problem> problems = GuiceUtil.bindConfig(configProps, target, true, false);
        assertEquals("" + problems, 0, problems.size());

        assertEquals("default", target.someVal);

    }

    static class TestBindConfigOptionalClass {
        @com.google.inject.Inject(optional = true)
        @Named("some.val")
        String someVal = "default";
    }

    static class TestBindConfigClass {
        @Inject
        @Named("some.val")
        private String someValJavax;

        public String getSomeValJavax() {
            return someValJavax;
        }

        @com.google.inject.Inject
        @com.google.inject.name.Named("some.val")
        String someValGoogle;

        @Inject
        @Named("some.int")
        Integer someInt;

        @Inject
        @Named("some.int")
        int someIntPrimitive;

        @Inject
        @Named("some.long")
        Long someLong;

        @Inject
        @Named("some.bool")
        Boolean someBool;

        @com.google.inject.Inject(optional = true)
        @Named("optional.string.google")
        String optionalStringGoogle;
    }

}
