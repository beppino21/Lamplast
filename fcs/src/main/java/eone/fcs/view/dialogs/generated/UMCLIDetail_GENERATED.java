package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.UMCLIDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMCLIDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOUMCLI>
{
    public UMCLIDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/UMCLIDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.UMCLIDetail}"; }
    public DCUmcli getDataContext() { return (DCUmcli)super.getDataContext(); }
    public UMCLIController getController() { return (UMCLIController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOUMCLI bean = new DOUMCLI();
        DCUmcli dc = new DCUmcli(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
