package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSEKETBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSEKET>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSEKET selectedBean)
    {
        FCSEKETDetail result = new FCSEKETDetail();
        return result;
    }
    
    @Override
    public FCSEKETListController getListController() { return (FCSEKETListController)super.getListController(); }
}
