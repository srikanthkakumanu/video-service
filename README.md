# video-service

A basic microservice for videos

### Run

The following command builds an image and tags it as srikanthkakumanu/video-service and runs the Docker image locally. The build creates a spring user and spring group to run the application. 

```docker build --build-arg JAR_FILE=build/libs/video-service-1.0.jar -t srikanthkakumanu/video-service .```


Run the application with user privileges helps to mitigate some risks. So, an important improvement to the Dockerfile is to run the application as a non-root user.
