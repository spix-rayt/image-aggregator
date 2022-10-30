# image-aggregator
Background process which download new images from Reddit and Joyreactor

## config.yml
```yaml
# Web UI settings
webUi:
  # localhost port
  port: 8080
# Joyreactor settings
joyreactor:
  # Tags list for find, filter and download images
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
  # Subreddits list for find, filter and download images
  subreddits:
    - pics
    - EarthPorn
```

## Roadmap

- ☑ Reddit
- ☑ Joyreactor
- Vkontakte
- Telegram
- Web interface
- Image organizer