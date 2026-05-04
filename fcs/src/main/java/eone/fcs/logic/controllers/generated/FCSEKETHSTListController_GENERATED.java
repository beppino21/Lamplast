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
import eone.fcs.logic.controllers.FCSEKETHSTController;

@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSEKETHSTListController_GENERATED
    extends BeanListControllerBaseDOFW<DOFCSEKETHST,DCDataContext<?>>
{
    public FCSEKETHSTListController_GENERATED(ENUMEditMode editMode, DCDataContext<?> dataContext, String embeddedId)
    {
        super(editMode,dataContext,DOFCSEKETHST.class,embeddedId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DOFCSEKETHST> readBeans() 
    { 
        return (List<DOFCSEKETHST>)getContext().getObjects(new DCKey(getEmbeddedId())); 
    }
    
    @Override
    public DOFCSEKETHST createNewItem()
    {
        DOFCSEKETHST bean = createNewInstance();
        initializeNewItem(bean);
        getContext().addListObject(new DCKey(getEmbeddedId()),bean);
        return bean;
    }
    
    public DOFCSEKETHST createNewInstance()
    {
        DOFCSEKETHST bean = new DOFCSEKETHST();
        return bean;
    }
    
    @Override
    public void removeBean(DOFCSEKETHST bean) 
    { 
        getContext().removeListObject(new DCKey(getEmbeddedId()),bean); 
    }
    
    @Override
    protected FCSEKETHSTController getControllerForItem(DOFCSEKETHST item)
    {
        return (FCSEKETHSTController)super.getControllerForItem(item);
    }
    
    

}
