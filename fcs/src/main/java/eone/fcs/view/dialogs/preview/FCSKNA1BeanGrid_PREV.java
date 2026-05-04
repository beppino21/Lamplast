package eone.fcs.view.dialogs.preview;

import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.view.app.ENUMEditMode;
import org.eclnt.jsfserver.managedbean.preview.IPreviewInstanceConfigurator;

import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.view.dialogs.*;
import eone.fcs.data.entities.DOFCSKNA1;

public class FCSKNA1BeanGrid_PREV implements IPreviewInstanceConfigurator<FCSKNA1BeanGrid>
{
    @Override
    public void configureForPreview(String beanName, FCSKNA1BeanGrid instance)
    {
        DCDataContextDummy dc = new DCDataContextDummy(new Object());
        instance.prepare(ENUMEditMode.EDIT,dc,DOFCSKNA1.class,"dummy",null);
    }
}
