java ^
   -agentpath:..\..\com.amd.aparapi.jni\dist\aparapi_x86_64.dll ^
   -Dcom.amd.aparapi.executionMode=%1 ^
   -Dcom.amd.aparapi.enableShowGeneratedOpenCL=true ^
   -Dcom.amd.aparapi.enableShowFakeLocalVariableTable=true ^
   -Dsize=%2 ^
   -Diterations=%3 ^
   -classpath blackscholes.jar;..\..\com.amd.aparapi\dist\aparapi.jar ^
   com.amd.aparapi.samples.blackscholes.LambdaMain 
