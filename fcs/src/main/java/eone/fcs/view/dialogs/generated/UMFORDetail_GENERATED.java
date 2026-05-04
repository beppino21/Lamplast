package eone.fcs.view.dialogs.generated;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.logic.controllers.*;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;
import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

@CCGenClass (expressionBase="#{d.UMFORDetail}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMFORDetail_GENERATED
    extends EditorBeanInstanceFrameOutestEditor<DOUMFOR>
{
    public UMFORDetail_GENERATED()
    {
        //prepareWithNewBean();
    }

    public String getPageName() { return "/eone/fcs/view/dialogs/UMFORDetail.xml"; }
    public String getRootExpressionUsedInPage() { return "#{d.UMFORDetail}"; }
    public DCUmfor getDataContext() { return (DCUmfor)super.getDataContext(); }
    public UMFORController getController() { return (UMFORController)super.getController(); }
    
    protected void prepareWithNewBean()
    {
        DOUMFOR bean = new DOUMFOR();
        DCUmfor dc = new DCUmfor(bean);
        prepare(ENUMEditMode.NEW,dc,bean);
    }
}
