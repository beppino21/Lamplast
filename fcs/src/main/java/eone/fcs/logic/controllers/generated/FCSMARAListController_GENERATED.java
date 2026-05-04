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
import eone.fcs.logic.controllers.FCSMARAController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMARAListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSMARA,DCDataContext<?>>
{
    public FCSMARAListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSMARA.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSMARA> readBeans() 
    { 
        return (List<DOFCSMARA>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSMARA createNewItem()
    {
        DOFCSMARA bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSMARA createNewInstance()
    {
        DOFCSMARA bean = new DOFCSMARA();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSMARA bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSMARAController getControllerForItem(DOFCSMARA item)
    {
        return (FCSMARAController)super.getControllerForItem(item);
    }
    
    

}
