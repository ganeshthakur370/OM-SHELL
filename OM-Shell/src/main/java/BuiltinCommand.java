import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public interface BuiltinCommand {



    

    int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) throws Exception;



    

    String getName();



    

    String getDescription();



    

    String getUsage();

}
