# Papra Share

THIS IS A FORK OF PARTY UP! I developped it since I was annoyed about having to move to the papra app

[Papra](https://papra.app/)
[partyup](https://github.com/9001/party-up)
[copyparty](https://github.com/9001/copyparty)


<a href="https://f-droid.org/packages/me.ocv.partyup/"><img src="https://ocv.me/fdroid.png" alt="Get it on F-Droid" height="50" /></a> '' <img src="https://img.shields.io/f-droid/v/me.ocv.partyup.svg" alt="f-droid version info" /> '' <a href="https://github.com/9001/party-up"><img src="https://img.shields.io/github/release/9001/party-up.svg?logo=github" alt="github version info" /></a>

upload files and links to a [copyparty](https://github.com/9001/copyparty) server by sharing them to this app


## basic usage

![screenshots showing the workflow of sharing a picture to this app](metadata/en-US/images/featureGraphic.png)

* install the app
* open it and set your server url (and password if applicable)

now all share buttons in other apps/browsers will have "Papra Share" as an option, letting you upload pics / vids / twitter links / anything really


## in case of permission errors

as of Android 11 (SDK30), a new API for sharing files was unfortunately enforced

if you get `Error3: java.io.FileNotFoundException` then that's because you are sharing files from an app which is still using the old API, which has now become forbidden for new apps to use

if you really need to share files from such outdated apps, then you'll have to use Papra Share 1.6.0 or older -- these versions were compiled for Android 9 (SDK28) which gives them permission to use the old API