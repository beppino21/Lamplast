package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.FCSMARABeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMARABeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOFCSMARA>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMARABeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMARABeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOFCSMARA selectedBean)
    {
        FCSMARADetail result = new FCSMARADetail();
        return result;
    }
    
    @Override
    public FCSMARAListController getListController() { return (FCSMARAListController)super.getListController(); }
}
