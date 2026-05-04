package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSEKETHSTBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETHSTBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSEKETHST>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETHSTBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETHSTBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSEKETHST selectedBean)
    {
        FCSEKETHSTDetail result = new FCSEKETHSTDetail();
        return result;
    }
    
    @Override
    public FCSEKETHSTListController getListController() { return (FCSEKETHSTListController)super.getListController(); }
}
