package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSMOVSAPDetail_PREV
    implements IPreviewInstanceConfigurator<FCSMOVSAPDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSMOVSAPDetail instance)
    {
        DOFCSMOVSAP bean = new DOFCSMOVSAP();
        DCFcsmovsap dc = new DCFcsmovsap(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
