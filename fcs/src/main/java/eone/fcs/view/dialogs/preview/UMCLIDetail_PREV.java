package eone.fcs.view.dialogs.preview;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.view.dialogs.*;

import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.dataapp.view.app.util.EditorBeanInstanceFrameOutestEditor;

public class UMCLIDetail_PREV
    implements IPreviewInstanceConfigurator<UMCLIDetail>
{
    @Override
    public void configureForPreview(String beanName, UMCLIDetail instance)
    {
        DOUMCLI bean = new DOUMCLI();
        DCUmcli dc = new DCUmcli(bean);
        instance.prepare(ENUMEditMode.NEW,dc,bean);
    }
}
