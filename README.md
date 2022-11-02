# image-aggregator
Background process which download new images from VK, Reddit and Joyreactor

## Web UI pages

**Screening out images** - watch random image and select save or delete  
**Images battle** - watch two random images and select which better  
**Top 50 images** - list of best images based on comparison  
**Metrics** - Count and size of images  


## config.yml - create this file before run app
```yaml
# Web UI settings
webUi:
  # localhost port
  port: 8080
# Joyreactor settings
joyreactor:
  # Tags list for download jpegs
  tags:
    - Anime
    - Overwatch

# Reddit settings
reddit:
  # Personal use script application (https://www.reddit.com/prefs/apps)
  # Username = App ID
  # Password = App secret
  appCredentials:
    username: R8pq0J2v_yw7Hou61X64Qg
    password: G0TncQ3Ui5A72A1UfORs0hdBbaW8cn
  # Reddit user login and password
  userCredentials:
    username: AverageImageCollectingEnjoyer
    password: eW91YXJlaGFja2VyPz8/
  # Subreddits list for download jpegs
  subreddits:
    - pics
    - EarthPorn
# VK settings
vk:
  # VK access_token
  accessToken: vk1.a.qSH83n9brASpirkdw4heaYBOi4UhOaBuuS0nxNveVIsOBJEaNOwdUbDTyTn2GnLRyxZ3PpMKEP2H-7qpvPr3eJXCR4VNlt3tDozB2yHSxFT72h-zJrevSexdx-ABZgyoxyBHIjIZVFHw4QIVB2g4lKJiglnV2eWJoRKboXEnC5jGvI4am9hKqklW6r6mcK9s8cd-zZgrWr4GoueZ5F2vWA
  # VK clubs list for download jpegs
  clubs:
    - art.photography
    - esthe
```

## Roadmap

- ☑ Reddit
- ☑ Joyreactor
- ☑ Vkontakte
- Telegram
- ☑ Web interface
- Image organizer
