package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSKNA1BeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSKNA1BeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSKNA1>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSKNA1BeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSKNA1BeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSKNA1 selectedBean)
    {
        FCSKNA1Detail result = new FCSKNA1Detail();
        return result;
    }
    
    @Override
    public FCSKNA1ListController getListController() { return (FCSKNA1ListController)super.getListController(); }
}
