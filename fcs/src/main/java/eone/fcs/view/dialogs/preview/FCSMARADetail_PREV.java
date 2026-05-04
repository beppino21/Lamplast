package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class FCSMARADetail_PREV
    implements IPreviewInstanceConfigurator<FCSMARADetail>
{
    @Override
    public void configureForPreview(String beanName, FCSMARADetail instance)
    {
        DOFCSMARA bean = new DOFCSMARA();
        DCFcsmara dc = new DCFcsmara(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
