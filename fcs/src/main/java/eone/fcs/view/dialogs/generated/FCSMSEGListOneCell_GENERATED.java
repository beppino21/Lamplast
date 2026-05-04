package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIFCSMSEG;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.FCSMSEGListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class FCSMSEGListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOFCSMSEG> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public FCSMSEGListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/FCSMSEGListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.FCSMSEGListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOFCSMSEG> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOFCSMSEG> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOFCSMSEG> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOFCSMSEG.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOFCSMSEG> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMSEG>(DOFCSMSEG.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOFCSMSEG> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOFCSMSEG>(DOFCSMSEG.class)
        {
            @Override
            protected IDataGridWrapper<DOFCSMSEG> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOFCSMSEG,VIFCSMSEG>(DOFCSMSEG.class,VIFCSMSEG.class)
                {
                    @Override
                    protected String findAvatarIconText(VIFCSMSEG itemObject)
                    {
                        return FCSMSEGListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIFCSMSEG itemObject)
                    {
                        return FCSMSEGListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOFCSMSEG> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOFCSMSEG>(DOFCSMSEG.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOFCSMSEG> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOFCSMSEG>(this);
    }
    
    protected String findAvatarIconText(VIFCSMSEG itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIFCSMSEG itemObject)
    {
        return null;
    }
}
