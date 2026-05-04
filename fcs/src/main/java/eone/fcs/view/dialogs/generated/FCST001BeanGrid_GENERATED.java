package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCST001BeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCST001BeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCST001>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCST001BeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCST001BeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCST001 selectedBean)
    {
        FCST001Detail result = new FCST001Detail();
        return result;
    }
    
    @Override
    public FCST001ListController getListController() { return (FCST001ListController)super.getListController(); }
}
