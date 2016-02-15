package de.deverado.framework.guice;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import de.deverado.framework.core.ParsingUtil;
import de.deverado.framework.core.Strings2;
import de.deverado.framework.core.problemreporting.Problem;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@ParametersAreNonnullByDefault
public class GuiceUtil {

    /**
     *
     * @param defaultVal
     *            returned if classNameParam is empty
     */
    public static Module getModuleByClassName(String classNameParam,
            Class<? extends Module> defaultVal) {
        try {
            Class<?> toCreate = defaultVal;
            if (!Strings2.isNullOrWhitespace(classNameParam)) {
                toCreate = GuiceUtil.class.getClassLoader().loadClass(
                        classNameParam);
            }

            Object newInstance = toCreate.newInstance();
            if (!(newInstance instanceof Module)) {
                throw new IllegalArgumentException("Given class "
                        + classNameParam + " is not a " + Module.class);
            }
            return (Module) newInstance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    public static List<Problem> bindConfig(Properties configProps, Object target, boolean tryForceInaccessibleFields,
                                           boolean injectWhenNamedWithoutInjectPresent) {
        List<Problem> result = new ArrayList<>();

        List<Field> allFieldsList = FieldUtils.getAllFieldsList(target.getClass());
        for (Field f : allFieldsList) {
            Pair<String, Boolean> configKeyAndOpt = extractInjectConfigForField(f, injectWhenNamedWithoutInjectPresent);
            if (configKeyAndOpt != null) {

                String configVal = configProps.getProperty(configKeyAndOpt.getLeft());
                if (configVal == null) {
                    if (!configKeyAndOpt.getRight()) {

                        result.add(new Problem("" + f).message(
                                "Config key '" + configKeyAndOpt.getLeft() + "' not present in config, " +
                                        "but required for " + target.getClass()));
                    }
                } else {
                    try {

                        writeFieldWithPossibleConversion(target, tryForceInaccessibleFields, f, configVal);
                    } catch (IllegalAccessException e) {
                        result.add(new Problem("" + f).message("Could not write field " + f + ": " + e));
                    } catch (IllegalArgumentException e) {
                        result.add(new Problem("" + f).message(
                                "Could not write field " + f + " with config value '" + configVal + "': " + e));
                    }
                }
            }
        }

        return result;
    }

    private static void writeFieldWithPossibleConversion(Object target, boolean tryForceInaccessibleFields, Field f,
                                                         String configVal) throws IllegalAccessException {
        Class<?> fieldType = f.getType();
        Object toSet = configVal;
        if (fieldType.equals(Boolean.class) || (fieldType.isPrimitive() && "boolean".equals(fieldType.getName()))) {
            toSet = ParsingUtil.parseAsBoolean(configVal);

        } else if (fieldType.equals(Long.class) || (fieldType.isPrimitive() && "long".equals(fieldType.getName()))) {
            try {
                toSet = ParsingUtil.parseAsLong(configVal, null);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not parseable as Long: '" + configVal + "'");
            }

        } else if (fieldType.equals(Integer.class) || (fieldType.isPrimitive() && "int".equals(fieldType.getName()))) {
            try {
                toSet = ParsingUtil.parseAsInt(configVal, null);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not parseable as Integer: '" + configVal + "'");
            }
        } else if (!fieldType.equals(String.class)) {
            throw new IllegalArgumentException("Cannot write to field with type " + fieldType);
        }
        FieldUtils.writeField(f, target, toSet, tryForceInaccessibleFields);
    }

    @Nullable
    private static Pair<String, Boolean> extractInjectConfigForField(Field f, boolean injectWhenNamedWithoutInjectPresent) {
        Annotation[] annotations = f.getAnnotations();
        boolean foundInjectAnnotation = false;
        String key = null;
        boolean optional = false;
        for (Annotation a : annotations) {

            if (a.annotationType().equals(Inject.class)) {
                foundInjectAnnotation = true;

                Inject injectAnn = (Inject) a;
                optional = ((Inject) a).optional();
            } else if (a.annotationType().equals(javax.inject.Inject.class)) {
                foundInjectAnnotation = true;

            } else if (a.annotationType().equals(Named.class)) {
                Named namedAnn = (Named) a;
                key = namedAnn.value();
            } else if (a.annotationType().equals(javax.inject.Named.class)) {
                javax.inject.Named namedAnn = (javax.inject.Named) a;
                key = namedAnn.value();
            }
        }
        if (key != null && (injectWhenNamedWithoutInjectPresent || foundInjectAnnotation)) {
            return Pair.of(key, optional);
        }
        return null;
    }
}
