package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSMSEGDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSMSEG>
{
    public FCSMSEGDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGDetail}"; }
    public DCFcsmseg getDataContext() { return (DCFcsmseg)super.getDataContext(); }
    public FCSMSEGController getController() { return (FCSMSEGController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSMSEG bean = new DOFCSMSEG();
        DCFcsmseg dc = new DCFcsmseg(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
