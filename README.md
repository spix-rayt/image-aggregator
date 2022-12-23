# Image Aggregator
Background process which download new (last N) images from VK, Reddit and Joyreactor

## Web UI pages

1. **Find similar images** - service slowly analyze all images from download and pass directories.   
Then you can find similar images, and select which image should be saved or deleted.  
   Keymap:
    - **←** - save left side image and delete* right side image
    - **→** - save right side image and delete* left side image
    - **DEL** - delete* both images
    - **SPACE** - save both images
    - **PAGE UP** - switch to combined view and show left image
    - **PAGE DOWN** - switch to combined view and show right image
    - **HOME** - switch to split view
2. **Screening out images** - watch random image and select save or delete  
   Keymap:
   - **→** - save image
   - **DEL** - delete* image
3. **Images battle** - watch two random images and select which is better  
   Keymap:
   - **←** - mark left image is better than right image
   - **→** - mark right image is better than left image
   - **SPACE** - skip this pair of images. it will be shown later
4. **Top 50 images** - list of best images based on image battle  
5. **Metrics** - Count and size of images  

\* Delete - instead of deleting image file service always moves it to image/trash directory

## Run native
create config file _config.yml_

##### run from source
```
./gradlew run
```

##### or build jar and run
```
./gradlew customFatJar
java -jar build/libs/all-in-one-jar-0.1-SNAPSHOT.jar
```

## Run in docker
create workdir _/root/appworkdir_  
create config file _/root/appworkdir/config.yml_  
```
./gradlew customFatJar
docker image build -t image-aggregator .
docker run -p 8080:8080 -v /root/appworkdir/:/workdir -i -t image-aggregator
```




## config.yml
```yaml
# Web UI settings
webUi:
  # localhost port
  port: 8080


# Joyreactor settings example
joyreactor:
  # Tags list for download jpegs
  tags:
    - Anime
    - Overwatch


# Reddit settings example
reddit:
  # "Personal use script" application (https://www.reddit.com/prefs/apps)
  # Username = Your app ID
  # Password = Your app secret
  appCredentials:
    username: R8pq0J2v_yw7Hou61X64Qg
    password: G0TncQ3Ui5A72A1UfORs0hdBbaW8cn
  # your user account login and password
  userCredentials:
    username: AverageImageCollectingEnjoyer
    password: eW91YXJlaGFja2VyPz8/
  # Subreddits list for download jpegs
  subreddits:
    - pics
    - EarthPorn


# VK settings example
vk:
  # VK access_token of your app
  accessToken: vk1.a.qSH83n9brASpirkdw4heaYBOi4UhOaBuuS0nxNveVIsOBJEaNOwdUbDTyTn2GnLRyxZ3PpMKEP2H-7qpvPr3eJXCR4VNlt3tDozB2yHSxFT72h-zJrevSexdx-ABZgyoxyBHIjIZVFHw4QIVB2g4lKJiglnV2eWJoRKboXEnC5jGvI4am9hKqklW6r6mcK9s8cd-zZgrWr4GoueZ5F2vWA
  # VK clubs list for download jpegs
  clubs:
    - art.photography
    - esthe

# Telegram settings example
telegram:
  # Your phone number for sign in telegram account
  phone: +380399958165
  # Telegram internal chat IDs (optional. run app for get chat ids)
  # All chatIds from your telegram account will be logged after successful auth
  chatIds:
    - -123
    - -456
```

## Roadmap

#### platforms
- [x] Reddit <sup>_(Reddit API + OkHttp)_</sup>
- [x] Joyreactor <sup>_(Joyreactor GraphQL API + GraphQL Kotlin + OkHttp)_</sup>
- [x] Vkontakte <sup>_(VK API + OkHttp)_</sup>
- [x] Telegram <sup>_(Experimental, only linux_64. TDLib + JNI)_</sup>
  - [x] linux_64
  - [ ] osx_64
  - [ ] windows_64
  - [ ] config rework
  - [ ] log in rework

#### tools
- [x] Web interface
- [x] Similar images finder
- [ ] Image organizer

#### other
- [x] Docker (archlinux amd64)