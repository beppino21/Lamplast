package eone.fcs.view.dialogs.generated;

import java.io.Serializable;

import org.eclnt.editor.annotations.CCGenClass;
import org.eclnt.jsfserver.pagebean.PageBean;

import eone.fcs.data.datacontexts.*;
import eone.fcs.data.entities.*;
import eone.fcs.data.entities.VIUMCLI;
import eone.fcs.view.dialogs.*;
import eone.fcs.view.dialogs.*;
import org.eclnt.jsfserver.pagebean.PageBean;

import org.eclnt.dataapp.controller.app.*;
import org.eclnt.dataapp.view.app.*;
import org.eclnt.dataapp.view.app.datagridmgmt.*;

@CCGenClass (expressionBase="#{d.UMCLIListOneCell}")
@SuppressWarnings({"rawtypes","unchecked","unused"})
public abstract class UMCLIListOneCell_GENERATED
    extends PageBean
    implements Serializable
{
    // ------------------------------------------------------------------------
    // members
    // ------------------------------------------------------------------------

    protected BeanDataGridOneCellWithEditor<DOUMCLI> m_gridWithEditor;

    // ------------------------------------------------------------------------
    // constructors & initialization
    // ------------------------------------------------------------------------

    public UMCLIListOneCell_GENERATED()
    {
        createGridWithEditor();
    }

    @Override
    public String getPageName() { return "/eone/fcs/view/dialogs/UMCLIListOneCell.xml"; }
    @Override
    public String getRootExpressionUsedInPage() { return "#{d.UMCLIListOneCell}"; }

    // ------------------------------------------------------------------------
    // public usage
    // ------------------------------------------------------------------------

    public BeanDataGridWithEditor<DOUMCLI> getGridWithEditor() { return m_gridWithEditor; }
    
    public void createGridWithEditor()
    {
        m_gridWithEditor = createBeanDataGridWithEditorView();
        IBeanDataGridController<DOUMCLI> gridController = createGridController();
        BeanDataGridWithEditor.IListener<DOUMCLI> listener = createBeanDataGridWithEditorListener();
        m_gridWithEditor.prepare(DOUMCLI.class,gridController,listener);
    }    

    // ------------------------------------------------------------------------
    // private usage
    // ------------------------------------------------------------------------
    
    protected BeanDataGridOneCellWithEditor<DOUMCLI> createBeanDataGridWithEditorStraight()
    {
        return new BeanDataGridOneCellWithEditor<DOUMCLI>(DOUMCLI.class); 
    }
    
    protected BeanDataGridOneCellWithEditor<DOUMCLI> createBeanDataGridWithEditorView()
    {
        return new BeanDataGridOneCellWithEditor<DOUMCLI>(DOUMCLI.class)
        {
            @Override
            protected IDataGridWrapper<DOUMCLI> createDataGridViewWrapper(Class beanClass)
            {
                return new CCDataGridView2OneCellDOFWWrapperWithViewMapping<DOUMCLI,VIUMCLI>(DOUMCLI.class,VIUMCLI.class)
                {
                    @Override
                    protected String findAvatarIconText(VIUMCLI itemObject)
                    {
                        return UMCLIListOneCell_GENERATED.this.findAvatarIconText(itemObject);
                    }
                    @Override
                    protected String findAvatarIconImage(VIUMCLI itemObject)
                    {
                        return UMCLIListOneCell_GENERATED.this.findAvatarIconImage(itemObject);
                    }
                };
            }
        }; 
    }
    
    protected IBeanDataGridController<DOUMCLI> createGridController()
    {
        return new BeanInstanceDataGridControllerDOFW<DOUMCLI>(DOUMCLI.class);
    }
    
    protected BeanDataGridWithEditor.IListener<DOUMCLI> createBeanDataGridWithEditorListener()
    {
        return new DefaultBeanListWithEditorListener<DOUMCLI>(this);
    }
    
    protected String findAvatarIconText(VIUMCLI itemObject)
    {
        return null;
    }
    
    protected String findAvatarIconImage(VIUMCLI itemObject)
    {
        return null;
    }
}
