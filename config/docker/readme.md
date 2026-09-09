Docker is used to compile lambdas and create images of the compiled code to
run in AWS Lambda. To run Docker, we use a dedicated EC2 instance called
_lambda compilation server_.

Read "Compilation Server for Lambdas.pdf" in this project folder on how to
set up the compilation server.

There are two files in this folder, swap_builders.sh and compile_lambda.sh,
which should be copied to the compilation server and made executable.

**swap_builders.sh**

This script controls the Docker buiders used to compile the lambdas. See the
"Docker builder instances" section in the PDF document on using the script.

**compile_lambda.sh**

This script compiles the lambda code to a container image and sends the image
to the AWS ECR repository. The script is invoked via ssh by the command
specified in the system property "vf.lambda.build.command".
