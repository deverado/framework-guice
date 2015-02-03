package de.deverado.framework.guice;

import com.google.inject.Module;
import de.deverado.framework.core.Strings2;

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
}
