package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSEKETDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSEKET>
{
    public FCSEKETDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSEKETDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSEKETDetail}"; }
    public DCFcseket getDataContext() { return (DCFcseket)super.getDataContext(); }
    public FCSEKETController getController() { return (FCSEKETController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSEKET bean = new DOFCSEKET();
        DCFcseket dc = new DCFcseket(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
