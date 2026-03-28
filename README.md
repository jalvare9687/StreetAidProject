StreetAid
Real time food & resource access 
StreetAid was made for people to find nearby food, water, and essentials resources in real time 

Problem:
Nearly 19 million Americans live in food deserts  areas with limited access to affordable, healthy food.
In Atlanta, this issue is even more severe:
1 in 4 residents live in food desert communities
Many lack reliable transportation
Many rely on basic phones or limited internet access
Current solutions fall short:
Hotlines are slow
Apps require smartphones and data
Information is often not real-time
This makes the issue not just a lack of resources but a lack of accessible, real-time information.


The Solution:
StreetAid is a real-time resource access system designed for people in food deserts and those facing immediate need.
It allows users to quickly find:
Food
Water
Shelter
Community services
All in one place, with a focus on speed, accessibility, and simplicity.


Features:
-Real time look ups:
	-Food banks,water access, Food  pantries, communities services ,cooling and warming centers, snap and EBT services
-Water Safety insights 
	- EPA standards integration flags drinking water sources in the area, showing any violations.
-Air quality awareness 
-Allows users to know the current air quality to make better judgment in safety when walking  

-Web Dashboard
	-A visual map for volunteers, and users to better familiarize themselves with the surrounding areas and available options given. 

How it works:
User enters their current zipcode
The backend parses the request
API’s are queried 
OpenStreetMap (food locations)
EPA AirNow (air quality)
EPA ECHO (water safety)
Claude
Results are shown based on distance 
AI summary is also provided giving a warm comprehensive breakdown of nearby resources 

What Makes this Program Run
Backend:
Java
Spring boot 
Vanilla Java script 
PostgreSQL
Frontend:
HTML/CSS/Java script 
Google Maps API
API’s & Integration:

OpenStreetMap Overpass API
Google Maps API
EPA AirNow API
EPA ECHO / SDWIS API
Claude (AI summaries)

Future Implementations:
StreetAid was built in a limited time frame, but its scalability is not limited. We made Streetaid with the idea of real-world deployment and scalability. 
SMS integration (in progress)
-Enable full SMS functionality using Twilio
Allow users to text commands like “FOOD 30314” or “WATER 30314”
Ensure access for users without smartphones or internet
This was a core feature we began implementing but were unable to fully complete within the hackathon timeframe
Expansion To more Cities:
We want to scale beyond Atlanta, to other communities and cities with food deserts
Use nationwide datasets (e.g., USDA Food Access Atlas)
Adapt to different regional resource systems

