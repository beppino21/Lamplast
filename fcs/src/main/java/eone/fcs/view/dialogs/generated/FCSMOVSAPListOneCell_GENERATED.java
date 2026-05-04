package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMOVSAP;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSMOVSAPListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMOVSAPListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAP> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMOVSAPListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMOVSAPListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMOVSAPListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMOVSAP> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSMOVSAP> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMOVSAP> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMOVSAP.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAP> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMOVSAP>(DOFCSMOVSAP.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSMOVSAP> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMOVSAP>(DOFCSMOVSAP.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMOVSAP> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSMOVSAP,VIFCSMOVSAP>(DOFCSMOVSAP.class,VIFCSMOVSAP.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSMOVSAP itemObject)
                    {
                        return FCSMOVSAPListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSMOVSAP itemObject)
                    {
                        return FCSMOVSAPListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMOVSAP> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMOVSAP>(DOFCSMOVSAP.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMOVSAP> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSMOVSAP>(this);
    }
    
    protected String findAvatarIconText(VIFCSMOVSAP itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSMOVSAP itemObject)
    {
        return null;
    }
}
