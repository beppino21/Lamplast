package eone.fcs.view.dialogs.preview;

import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.view.dialogs.*;
import eone.fcs.data.entities.DOFCSMSEG;

public class FCSMSEGBeanGrid_PREV implements IPreviewInstanceConfigurator<FCSMSEGBeanGrid>
{
    @Override
    public void configureForPreview(String beanName, FCSMSEGBeanGrid instance)
    {
        DCDataContextDummy dc = new DCDataContextDummy(new Object());
        instance.prepare(ENUMEditMode.EDIT,dc,DOFCSMSEG.class,"dummy",null);
    }
}
