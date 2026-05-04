package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSLFA1Detail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSLFA1Detail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSLFA1>
{
    public FCSLFA1Detail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSLFA1Detail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSLFA1Detail}"; }
    public DCFcslfa1 getDataContext() { return (DCFcslfa1)super.getDataContext(); }
    public FCSLFA1Controller getController() { return (FCSLFA1Controller)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSLFA1 bean = new DOFCSLFA1();
        DCFcslfa1 dc = new DCFcslfa1(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
