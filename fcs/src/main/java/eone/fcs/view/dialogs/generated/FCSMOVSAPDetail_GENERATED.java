package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSMOVSAPDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSMOVSAP>
{
    public FCSMOVSAPDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPDetail}"; }
    public DCFcsmovsap getDataContext() { return (DCFcsmovsap)super.getDataContext(); }
    public FCSMOVSAPController getController() { return (FCSMOVSAPController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSMOVSAP bean = new DOFCSMOVSAP();
        DCFcsmovsap dc = new DCFcsmovsap(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
