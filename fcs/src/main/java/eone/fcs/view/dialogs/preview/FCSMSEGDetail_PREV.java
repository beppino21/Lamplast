package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSMSEGDetail_PREV
    implements IPreviewInstanceConfigurator<FCSMSEGDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSMSEGDetail instance)
    {
        DOFCSMSEG bean = new DOFCSMSEG();
        DCFcsmseg dc = new DCFcsmseg(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
