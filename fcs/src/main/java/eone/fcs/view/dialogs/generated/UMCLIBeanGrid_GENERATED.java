package eone.fcs.view.dialogs.generated;

import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.IPageBean;

import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.dataapp.view.app.util.EditorBeanGridFrameListEditor;
import eone.fcs.logic.controllers.*;

@CCGenClass (expressionBase="#{d.UMCLIBeanGrid}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMCLIBeanGrid_GENERATED
    extends EditorBeanGridFrameListEditor<DOUMCLI>  
{
    public String getPageName() { return "/eone/fcs/view/dialogs/UMCLIBeanGrid.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.UMCLIBeanGrid}"; }
    
    @Override
    protected IPageBean createDetailUIForSelectedBean(DOUMCLI selectedBean)
    {
        UMCLIDetail result = new UMCLIDetail();
        return result;
    }
    
    @Override
    public UMCLIListController getListController() { return (UMCLIListController)super.getListController(); }
}
