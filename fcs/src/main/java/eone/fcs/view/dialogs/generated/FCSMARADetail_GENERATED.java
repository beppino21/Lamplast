package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.FCSMARADetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMARADetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOFCSMARA>
{
    public FCSMARADetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMARADetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.FCSMARADetail}"; }
    public DCFcsmara getDataContext() { return (DCFcsmara)super.getDataContext(); }
    public FCSMARAController getController() { return (FCSMARAController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOFCSMARA bean = new DOFCSMARA();
        DCFcsmara dc = new DCFcsmara(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
