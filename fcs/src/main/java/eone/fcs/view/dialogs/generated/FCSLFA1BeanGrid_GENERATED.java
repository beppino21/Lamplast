package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSLFA1BeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1BeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSLFA1>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSLFA1BeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSLFA1BeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSLFA1 selectedBean)
    {
        FCSLFA1Detail result = new FCSLFA1Detail();
        return result;
    }
    
    @Override
    public FCSLFA1ListController getListController() { return (FCSLFA1ListController)super.getListController(); }
}
