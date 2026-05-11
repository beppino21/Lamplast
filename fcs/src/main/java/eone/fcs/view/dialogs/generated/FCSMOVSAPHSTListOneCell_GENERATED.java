package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMOVSAPHST;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSMOVSAPHSTListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPHSTListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAPHST> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMOVSAPHSTListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPHSTListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPHSTListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMOVSAPHST> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSMOVSAPHST> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMOVSAPHST> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMOVSAPHST.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAPHST> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMOVSAPHST>(DOFCSMOVSAPHST.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAPHST> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMOVSAPHST>(DOFCSMOVSAPHST.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMOVSAPHST> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSMOVSAPHST,VIFCSMOVSAPHST>(DOFCSMOVSAPHST.class,VIFCSMOVSAPHST.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSMOVSAPHST itemObject)
                    {
                        return FCSMOVSAPHSTListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSMOVSAPHST itemObject)
                    {
                        return FCSMOVSAPHSTListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMOVSAPHST> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMOVSAPHST>(DOFCSMOVSAPHST.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMOVSAPHST> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSMOVSAPHST>(this);
    }
    
    protected String findAvatarIconText(VIFCSMOVSAPHST itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSMOVSAPHST itemObject)
    {
        return null;
    }
}
