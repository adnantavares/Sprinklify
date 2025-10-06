**Sprinklify**

Sprinklify is an Android application that turns decades of NASA reanalysis data into actionable weather-risk information for any location and date. Built for the “Will It Rain on My Parade” Space Apps challenge, Sprinklify offers both a Simple and Precise mode:

**Simple Mode**

Uses the NASA POWER API to fetch daily averages of temperature, precipitation, wind speed, and snowfall for the selected location and day of year.

Fast, lightweight, ideal for quick overviews.

**Precise Mode**

Accesses hourly data from NASA GES DISC’s MERRA-2 OPeNDAP ASCII endpoints, covering the past 40 years.

Computes hourly averages and frequency distributions.

Supports CSV export of raw time-series ASCII data.

**Key Technical Details**

Platform & Architecture

Kotlin & Android SDK

Navigation Component for fragment routing

Data Integration

NASA POWER REST API for global daily climatologies

NASA GES DISC OPeNDAP for MERRA-2 hourly single-level (SLV) and surface flux (FLX) datasets in ASCII format

Stream ID logic to select the correct MERRA-2 data stream (1980–1991 → 100, 1992–2000 → 200, 2001–2010 → 300, 2011–present → 400)

UI / UX

Google Maps fragment for location pin-drop

Material DatePicker for selecting date

Segmented toggle for Simple vs. Precise modes

MPAndroidChart for distribution and time-series graphs

CSV download button in Precise mode

##Data Processing

Asynchronous network calls with Retrofit + Coroutines

Parsing ASCII responses directly in Kotlin

Statistical analysis to compute climatological probabilities and extreme event thresholds

Local caching with Room for offline reuse

**Getting Started**

Clone the repository

In local.properties, add your keys:

MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY  
gesdisc.bearer.token=YOUR_GES_DISC_BEARER_TOKEN

Build and run in Android Studio

Select a location, pick a date, and toggle between modes to explore historical weather likelihoods

Sprinklify bridges user-friendly UI with robust NASA data services to empower event planners, outdoor enthusiasts, and developers with clear, data-driven weather insights.
