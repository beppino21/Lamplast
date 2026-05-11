package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSMOVSAPHSTDetail_PREV
    implements IPreviewInstanceConfigurator<FCSMOVSAPHSTDetail>
{
    @Override
    public void configureForPreview(String beanName, FCSMOVSAPHSTDetail instance)
    {
        DOFCSMOVSAPHST bean = new DOFCSMOVSAPHST();
        DCFcsmovsaphst dc = new DCFcsmovsaphst(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
