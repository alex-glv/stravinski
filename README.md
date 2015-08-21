# stravinski

Connects to twitter streaming api and forwards tweet information to Riemann server


## Usage

Run the docker container with the following env variables:
* APP_CONS_KEY - twitter app consumer key
* APP_CONS_SECRET - twitter app consumer secret
* APP_ACC_TOKEN - twitter app token
* APP_ACC_TOKEN_SECRET - twitter app token secret

Container will start webserver on port 7705
## Options

* GET /start - start streaming tweets to es instance
* GET /stop - stop stream


## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
