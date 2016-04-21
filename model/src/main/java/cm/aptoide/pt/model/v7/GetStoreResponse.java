/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 21/04/2016.
 */

package cm.aptoide.pt.model.v7;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TODO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GetStoreResponse extends BaseV7Response {

	private Nodes nodes;

	@Data
	public static class Nodes {

		private Meta meta;
		private Tabs tabs;
		private Widgets widgets;
	}

	public static class Meta extends BaseV7Response {

		public Data data;

		public static class Data {

			private Number id;
			private String name;
			private String avatar;
			private Appearance appearance;
			private Stats stats;

			public static class Stats {

				private Number apps;
				private Number subscribers;
				private Number downloads;
			}

			public static class Appearance {

				private String theme;
				private String description;
			}
		}
	}

	/**
	 * Created by hsousa on 17/09/15.
	 */
	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Tabs extends BaseV7Response {

		@JsonProperty("list") private List<Tab> tabList;

		@Data
		public static class Tab {

			private String label;
			private Event event;

			@Data
			public static class Event {

				public static final String GET_STORE_TAB = "getStore";
				public static final String GET_STORE_WIDGETS_TAB = "getStoreWidgets";
				public static final String GET_APK_COMMENTS_TAB = "getApkComments";
				public static final String GET_REVIEWS_TAB = "getReviews";

				public static final String API_V7_TYPE = "API";
				public static final String API_V3_TYPE = "v3";

				public static final String EVENT_LIST_APPS = "listApps";
				public static final String EVENT_LIST_STORES = "listStores";
				public static final String EVENT_GETSTOREWIDGETS = "getStoreWidgets";
				public static final String EVENT_GETAPKCOMMENTS = "getApkComments";

				private String type; // API, v3
				private String name; // listApps, getStore, getStoreWidgets, getApkComments
				private String action;
			}
		}
	}

	@Data
	@EqualsAndHashCode(callSuper = true)
	public static class Widgets extends BaseV7Response {

		private Datalist datalist;

		@Data
		public static class Datalist {

			@JsonProperty("list") public List<WidgetList> widgetList = new ArrayList<>();
			private Number total;
			private Number count;
			private Number next;
			private Number offset;
			private Number limit;
			private Number hidden;

			@Data
			public static class WidgetList {

				/**
				 * Constants for values of type
				 */
				public static final String ADS_TYPE = "ADS";
				public static final String APPS_GROUP_TYPE = "APPS_GROUP";
				public static final String CATEGORIES_TYPE = "DISPLAYS";
				public static final String TIMELINE_TYPE = "TIMELINE";
				public static final String REVIEWS_TYPE = "REVIEWS";
				public static final String COMMENTS_TYPE = "COMMENTS";
				public static final String STORE_GROUP = "STORES_GROUP";

				private String type;
				private String tag;
				private String title; // Highlighted, Games, Categories, Timeline, Recommended for you, Aptoide Publishers
				@JsonProperty("view") private String listApps;
				private List<Action> actions = new ArrayList<>();
				private Data data;

				@lombok.Data
				public static class Data {

					private String layout; // GRID, LIST, BRICK
					private String icon;
					private List<Categories> categories = new ArrayList<>(); //only present if type": "DISPLAYS"

					@lombok.Data
					public static class Categories {

						private Number id;
						private String ref_id;
						private String parent_id;
						private String parent_ref_id;
						private String name;
						private String graphic;
						private String icon;
						private Number ads_count;
					}
				}

				@lombok.Data
				public static class Action {

					private String type; // button
					private String label;
					private String tag;
					private Event event;

					@lombok.Data
					public static class Event {

						public static final String GET_STORE_TAB = "getStore";
						public static final String GET_STORE_WIDGETS_TAB = "getStoreWidgets";
						public static final String GET_APK_COMMENTS_TAB = "getApkComments";
						public static final String GET_REVIEWS_TAB = "getReviews";

						public static final String API_V7_TYPE = "API";
						public static final String API_V3_TYPE = "v3";

						public static final String EVENT_LIST_APPS = "listApps";
						public static final String EVENT_LIST_STORES = "listStores";
						public static final String EVENT_GETSTOREWIDGETS = "getStoreWidgets";
						public static final String EVENT_GETAPKCOMMENTS = "getApkComments";

						public String type; // API, v3
						public String name; // listApps, getStore, getStoreWidgets, getApkComments
						public String action;
					}
				}
			}
		}
	}
}
