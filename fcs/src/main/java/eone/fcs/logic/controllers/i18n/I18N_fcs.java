package eone.fcs.logic.controllers.i18n;

import eone.fcs.logic.controllers.i18n.generated.*;

public class I18N_fcs
    extends I18N_fcs_GENERATED
{
    public static String txt(String key)
    {
        return I18N_fcs_GENERATED.instance().lit(key);
    }
    
    public static String txt(String key, Object... params)
    {
        return I18N_fcs_GENERATED.instance().lit(key,params);
    }
}
