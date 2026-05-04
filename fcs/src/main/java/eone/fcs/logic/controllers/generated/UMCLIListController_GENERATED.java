package eone.fcs.logic.controllers.generated;

import java.util.List;

import org.eclnt.ccee.datacontext.*;
import org.eclnt.dataapp.logic.entities.datacontext.*;
import org.eclnt.dataapp.controller.app.IBeanInstanceController;
import org.eclnt.dataapp.view.app.ENUMEditMode;

import org.eclnt.dataapp.controller.app.BeanListControllerBaseDOFW;
import eone.fcs.data.entities.*;
import eone.fcs.data.datacontexts.*;
import eone.fcs.logic.controllers.*;
import eone.fcs.logic.controllers.UMCLIController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMCLIListController_GENERATED
    extends BeanListControllerBaseDOFW<DOUMCLI,DCDataContext<?>>
{
    public UMCLIListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOUMCLI.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOUMCLI> readBeans() 
    { 
        return (List<DOUMCLI>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOUMCLI createNewItem()
    {
        DOUMCLI bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOUMCLI createNewInstance()
    {
        DOUMCLI bean = new DOUMCLI();
        return bean;
    }
    
    @Override
    public void removeBean(DOUMCLI bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected UMCLIController getControllerForItem(DOUMCLI item)
    {
        return (UMCLIController)super.getControllerForItem(item);
    }
    
    

}
