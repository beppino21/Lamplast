package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class UMFORDetail_PREV
    implements IPreviewInstanceConfigurator<UMFORDetail>
{
    @Override
    public void configureForPreview(String beanName, UMFORDetail instance)
    {
        DOUMFOR bean = new DOUMFOR();
        DCUmfor dc = new DCUmfor(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
