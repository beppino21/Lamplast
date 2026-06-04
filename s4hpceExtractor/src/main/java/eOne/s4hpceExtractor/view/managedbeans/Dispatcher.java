package eOne.s4hpceExtractor.view.managedbeans;

import org.eclnt.workplace.IWorkpageContainer;
import org.eclnt.workplace.WorkpageDispatcher;

/*
 * The dispatcher is referenced in faces-config.xml. When changing the package
 * of the dispatcher, then also update the faces-config.xml link!
 */
public class Dispatcher extends WorkpageDispatcher
{
    public Dispatcher()
    {
    }

    public Dispatcher(IWorkpageContainer workpageContainer)
    {
        super(workpageContainer);
    }

    public static DispatcherInfo getStaticDispatcherInfo() { return new DispatcherInfo(Dispatcher.class); }

    public static Dispatcher getDialogSessionInstance()
    {
        return (Dispatcher)WorkpageDispatcher.getDialogSessionInstance();
    }
}
