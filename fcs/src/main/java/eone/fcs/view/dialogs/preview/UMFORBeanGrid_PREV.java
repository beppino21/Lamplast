package eone.fcs.view.dialogs.preview;

import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.view.dialogs.*;
import eone.fcs.data.entities.DOUMFOR;

public class UMFORBeanGrid_PREV implements IPreviewInstanceConfigurator<UMFORBeanGrid>
{
    @Override
    public void configureForPreview(String beanName, UMFORBeanGrid instance)
    {
        DCDataContextDummy dc = new DCDataContextDummy(new Object());
        instance.prepare(ENUMEditMode.EDIT,dc,DOUMFOR.class,"dummy",null);
    }
}
